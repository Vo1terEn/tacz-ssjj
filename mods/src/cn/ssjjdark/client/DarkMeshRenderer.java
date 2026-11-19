package cn.ssjjdark.client;

import cn.fusionarsenal.compat.TaCZClientCompat;
import cn.ssjjzeus.client.ZeusMeshRenderer;
import cn.ssjjdark.DarkSkins;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Original joint-local source triangles; native TaCZ owns the view and animation. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkMeshRenderer {
    private record Profile(ResourceLocation gun,ResourceLocation display,ZeusMeshRenderer.Geometry mesh) {}
    private static volatile List<Profile> profiles=List.of();
    private static final Map<BedrockAnimatedModel,Profile> bound=new WeakHashMap<>();
    private static final Map<BedrockAnimatedModel,Profile> rejected=new WeakHashMap<>();
    private DarkMeshRenderer() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerDarkCrystalSourceMeshes(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)DarkMeshRenderer::read);
        }
    }
    public static void read(ResourceManager resources) {
        profiles=List.of();bound.clear();rejected.clear();var result=new ArrayList<Profile>();
        var skins=new ArrayList<String>();skins.add("");skins.addAll(DarkSkins.SKINS);
        for(var gun:DarkSkins.GUNS) for(String skin:skins) {
            String tier=gun.getPath(),stem=tier+(skin.isEmpty()?"":"_"+skin);
            try(var stream=resources.open(new ResourceLocation("ssjj_dark","source_mesh_"+stem+".json"));
                var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                var mesh=new Gson().fromJson(reader,ZeusMeshRenderer.Geometry.class);ZeusMeshRenderer.validate(mesh);
                if(resources.getResource(new ResourceLocation("ssjj_dark","textures/gun/uv/"+stem+".png")).isEmpty())
                    throw new IllegalArgumentException("Missing Dark Crystal source texture "+stem);
                result.add(new Profile(gun,DarkSkins.display(gun,skin),mesh));
            } catch(Exception failure) {LogUtils.getLogger().error("Cannot read Dark Crystal source profile "+stem,failure);}
        }
        profiles=List.copyOf(result);
    }
    @SubscribeEvent public static void tickDarkCrystalMeshBindings(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(var profile:profiles)TaCZClientCompat.forEachGunDisplay(profile.display,profile.gun,display->bind(display,profile));
    }
    private static void bind(com.tacz.guns.client.resource.GunDisplayInstance display,Profile profile) {
        var model=display.getGunModel();
        if(model==null || bound.get(model)==profile || rejected.get(model)==profile)return;
        try {attach(model,profile.mesh,profile.display,display::getAnimationStateMachine);bound.put(model,profile);}
        catch(RuntimeException failure){rejected.put(model,profile);LogUtils.getLogger().error("Cannot bind Dark Crystal source profile "+profile.gun,failure);}
    }
    /** A scope edit mutates the native retained stack without creating a new draw instance. */
    @SubscribeEvent(priority=EventPriority.HIGH)
    public static void initializeDarkCrystalFirstPerson(RenderHandEvent event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        var mc=Minecraft.getInstance();if(mc==null || mc.player==null)return;
        var stack=TaCZClientCompat.currentRenderedItem();if(stack==null)return;
        var gun=IGun.getIGunOrNull(stack);if(gun==null || !DarkSkins.family(gun.getGunId(stack)))return;
        var held=mc.player.getMainHandItem();var heldGun=IGun.getIGunOrNull(held);
        if(heldGun==null || !gun.getGunId(stack).equals(heldGun.getGunId(held)))return;
        DarkSkins.sync(stack);
        var selected=TimelessAPI.getGunDisplay(stack).orElse(null);if(selected==null)return;
        var expected=DarkSkins.display(gun.getGunId(stack),DarkSkins.skin(gun.getAttachmentId(stack,AttachmentType.SCOPE)));
        for(var profile:profiles)if(profile.gun.equals(gun.getGunId(stack)) && profile.display.equals(expected))
            TaCZClientCompat.forEachGunDisplay(profile.display,profile.gun,display->{
                if(display==selected)bind(display,profile);
            });
        if(!bound.containsKey(selected.getGunModel()))return;
        var renderer=IClientItemExtensions.of(stack.getItem()).getCustomRenderer();
        if(renderer instanceof AnimateGeoItemRenderer<?,?> animated && animated.needReInit(stack))
            animated.tryInit(stack,mc.player,event.getPartialTick());
    }
    public static void attach(BedrockAnimatedModel model,ZeusMeshRenderer.Geometry mesh) {
        attach(model,mesh,null,null);
    }
    private static void attach(BedrockAnimatedModel model,ZeusMeshRenderer.Geometry mesh,ResourceLocation display,
        java.util.function.Supplier<com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine<?>> machine) {
        ZeusMeshRenderer.validate(mesh);
        Map<String,List<ZeusMeshRenderer.Part>> groups=new LinkedHashMap<>();
        for(var part:mesh.parts) {
            if(model.getNode(part.bone)==null)throw new IllegalArgumentException("Missing Dark Crystal source joint "+part.bone);
            groups.computeIfAbsent(part.bone,k->new ArrayList<>()).add(part);
        }
        ZeusMeshRenderer.lockRefitView(model);
        for(var entry:groups.entrySet()) {
            var parts=List.copyOf(entry.getValue());
            model.setFunctionalRenderer(entry.getKey(),node->(pose,consumer,context,light,overlay)->{
                for(var part:parts)ZeusMeshRenderer.emit(part,pose,consumer,light,overlay);
                if(display!=null)DarkSkinEffects.defer(display,model,entry.getKey(),pose,machine.get());
                for(var child:node.children)child.render(pose,context,consumer,light,overlay);
            });
        }
        DarkNativeHands.install(model);
    }
}
