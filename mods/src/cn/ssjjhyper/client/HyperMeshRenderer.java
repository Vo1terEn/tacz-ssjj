package cn.ssjjhyper.client;

import cn.fusionarsenal.compat.TaCZClientCompat;
import cn.ssjjhyper.HyperReloadProtection;
import cn.ssjjhyper.HyperScopes;
import cn.ssjjzeus.client.ZeusMeshRenderer;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reuses the established joint-local mesh format and native TaCZ item traversal. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperMeshRenderer {
    private record Profile(ResourceLocation gun,ResourceLocation display,String stem,ZeusMeshRenderer.Geometry mesh) {}
    private static volatile List<Profile> profiles=List.of();
    private static final Map<BedrockAnimatedModel,Profile> bound=new WeakHashMap<>();
    private static final Map<BedrockAnimatedModel,Profile> rejected=new WeakHashMap<>();
    private HyperMeshRenderer() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerHyperMeshResources(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)HyperMeshRenderer::read);
        }
    }
    public static void read(ResourceManager resources) {
        profiles=List.of();bound.clear();rejected.clear();var loaded=new ArrayList<Profile>();
        HyperBodyEffects.read(resources);
        for(ResourceLocation gun:HyperReloadProtection.GUNS) for(String skin:List.of("","totem","star","colorful_spar")) {
            String stem=gun.getPath()+(skin.isEmpty()?"":"_"+skin);
            var resource=new ResourceLocation("ssjj_hyper","source_mesh_"+stem+".json");
            try(var stream=resources.open(resource);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var mesh=new Gson().fromJson(reader,ZeusMeshRenderer.Geometry.class);
                ZeusMeshRenderer.validate(mesh);
                var texture=new ResourceLocation("ssjj_hyper","textures/gun/uv/"+stem+".png");
                if(resources.getResource(texture).isEmpty())throw new IllegalArgumentException("Missing Hyper Revolver atlas "+texture);
                loaded.add(new Profile(gun,HyperScopes.display(gun,skin),stem,mesh));
                LogUtils.getLogger().info("HYPER_REVOLVER {} source mesh loaded: {} joint-local parts",gun,mesh.parts.length);
            } catch(Exception failure) {LogUtils.getLogger().error("Cannot load Hyper Revolver source mesh "+gun,failure);}
        }
        profiles=List.copyOf(loaded);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(Profile profile:profiles)TaCZClientCompat.forEachGunDisplay(profile.display,profile.gun,display->{
            var model=display.getGunModel();
            if(model==null || bound.get(model)==profile || rejected.get(model)==profile)return;
            try {attach(model,profile.mesh,profile.gun,profile.stem);bound.put(model,profile);}
            catch(RuntimeException failure) {rejected.put(model,profile);LogUtils.getLogger().error("Cannot attach Hyper Revolver source mesh "+profile.gun,failure);}
        });
    }
    /** Cover retained animation instances when an outgoing item changes before the next tick. */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void initializeHyperFirstPerson(RenderHandEvent event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        var mc=Minecraft.getInstance();if(mc==null || mc.player==null)return;
        var stack=TaCZClientCompat.currentRenderedItem();if(stack==null)return;
        var gun=IGun.getIGunOrNull(stack);if(gun==null || !HyperReloadProtection.family(gun.getGunId(stack)))return;
        var held=mc.player.getMainHandItem();var heldGun=IGun.getIGunOrNull(held);
        if(heldGun==null || !gun.getGunId(stack).equals(heldGun.getGunId(held)))return;
        var display=TimelessAPI.getGunDisplay(stack).orElse(null);
        if(display==null || !bound.containsKey(display.getGunModel()))return;
        var renderer=IClientItemExtensions.of(stack.getItem()).getCustomRenderer();
        if(renderer instanceof AnimateGeoItemRenderer<?,?> animated && animated.needReInit(stack))
            animated.tryInit(stack,mc.player,event.getPartialTick());
    }
    public static void attach(BedrockAnimatedModel model,ZeusMeshRenderer.Geometry mesh,ResourceLocation gun) {
        attach(model,mesh,gun,gun.getPath());
    }
    public static void attach(BedrockAnimatedModel model,ZeusMeshRenderer.Geometry mesh,ResourceLocation gun,String stem) {
        if(!HyperReloadProtection.family(gun))throw new IllegalArgumentException("Unknown Hyper Revolver profile "+gun);
        ZeusMeshRenderer.validate(mesh);
        Map<String,List<ZeusMeshRenderer.Part>> groups=new LinkedHashMap<>();
        for(var part:mesh.parts) {
            if(model.getNode(part.bone)==null)throw new IllegalArgumentException("Missing Hyper Revolver joint "+part.bone);
            groups.computeIfAbsent(part.bone,key->new ArrayList<>()).add(part);
        }
        for(String bone:HyperBodyEffects.bones(stem)) {
            if(model.getNode(bone)==null)throw new IllegalArgumentException("Missing Hyper body FX joint "+bone);
            groups.computeIfAbsent(bone,key->new ArrayList<>());
        }
        ZeusMeshRenderer.lockRefitView(model);
        for(var entry:groups.entrySet()) {
            var parts=List.copyOf(entry.getValue());
            String bone=entry.getKey();
            model.setFunctionalRenderer(entry.getKey(),node->(pose,consumer,context,light,overlay)->{
                for(var part:parts)ZeusMeshRenderer.emit(part,pose,consumer,light,overlay);
                HyperBodyEffects.defer(model,stem,bone,pose);
                for(var child:node.children)child.render(pose,context,consumer,light,overlay);
            });
        }
        HyperNativeHands.install(model,gun);
        cn.ssjjcompat.SkinTailOrder.ensure(model);
    }
}
