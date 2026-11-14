package cn.ssjjzeus.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import cn.fusionarsenal.compat.TaCZClientCompat;
import cn.ssjjzeus.ZeusScopes;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** Zeus source triangles use TaCZ's animated joint transforms in every item view. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class ZeusMeshRenderer {
    public static final ResourceLocation GUN=new ResourceLocation("ssjj_zeus","hand_of_zeuss2");
    public static final ResourceLocation DISPLAY=new ResourceLocation("ssjj_zeus","hand_of_zeuss2_display");
    private record Profile(ResourceLocation gun,ResourceLocation display,ResourceLocation mesh,ResourceLocation effects) {}
    private record Loaded(Profile profile,Geometry geometry,cn.ssjjdimension.client.DimensionParticles.Data effects) {}
    private static final List<Profile> PROFILES=profiles();
    private static List<Profile> profiles() {
        List<Profile> result=new ArrayList<>();
        for(String id:List.of("hand_of_zeuss","hand_of_zeuss1","hand_of_zeuss2")) {
            result.add(profile(id,""));for(String skin:ZeusScopes.SKINS)result.add(profile(id,skin));
        }
        return List.copyOf(result);
    }
    private static Profile profile(String id,String skin) {
        boolean second=id.equals("hand_of_zeuss2");
        return new Profile(new ResourceLocation("ssjj_zeus",id),new ResourceLocation("ssjj_zeus",id+(skin.isEmpty()?"":"_"+skin)+"_display"),
            new ResourceLocation("ssjj_zeus",second?"source_mesh.json":"source_mesh_"+id+".json"),
            new ResourceLocation("ssjj_zeus",skin.isEmpty()?(second?"effects/body.json":"effects/body_"+id+".json"):"effects/body_"+id+"_"+skin+".json"));
    }
    public static final class Part {
        public String bone,texture,render;
        public float[][] positions,normals,uv;
        public int[][] triangles;
    }
    public static final class Geometry { public String texture;public Part[] parts; }
    private static volatile List<Loaded> loaded=List.of();
    private static final Map<BedrockAnimatedModel,Loaded> bound=new WeakHashMap<>();
    private static final Map<BedrockAnimatedModel,Loaded> rejected=new WeakHashMap<>();
    private ZeusMeshRenderer() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerZeusMeshResources(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ZeusMeshRenderer::read);
        }
    }
    public static void read(ResourceManager resources) {
        loaded=List.of();bound.clear();rejected.clear();List<Loaded> freshProfiles=new ArrayList<>();
        Map<ResourceLocation,Geometry> meshes=new LinkedHashMap<>();
        for(Profile profile:PROFILES) {
            Geometry mesh=meshes.get(profile.mesh);
            if(mesh==null) {
                try(var stream=resources.open(profile.mesh);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                    mesh=new Gson().fromJson(reader,Geometry.class);validate(mesh);meshes.put(profile.mesh,mesh);
                    LogUtils.getLogger().info("ZEUS_FAMILY {} source mesh loaded: {} joint-local parts",profile.gun,mesh.parts.length);
                } catch(Exception failure) {LogUtils.getLogger().error("Cannot load Zeus source mesh "+profile.gun,failure);continue;}
            }
            cn.ssjjdimension.client.DimensionParticles.Data effects=null;
            if(resources.getResource(profile.effects).isPresent()) {
                try(var stream=resources.open(profile.effects);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                    effects=new Gson().fromJson(reader,cn.ssjjdimension.client.DimensionParticles.Data.class);
                    cn.ssjjdimension.client.DimensionParticles.validate(effects,profile.gun.toString(),"WeaponAnchor3","ssjj_zeus:textures/effects/");
                    for(var emitter:effects.emitters)if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())
                        throw new IllegalArgumentException("Missing Zeus body-effect texture "+emitter.texture);
                } catch(Exception failure) {effects=null;LogUtils.getLogger().error("Cannot load Zeus source body effects "+profile.gun,failure);}
            }
            freshProfiles.add(new Loaded(profile,mesh,effects));
        }
        loaded=List.copyOf(freshProfiles);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        for(Loaded selected:loaded)TaCZClientCompat.forEachGunDisplay(selected.profile.display,selected.profile.gun,index->{
            BedrockAnimatedModel model=index.getGunModel();
            if(model==null || bound.get(model)==selected || rejected.get(model)==selected)return;
            try {attach(model,selected.geometry,selected.effects,selected.profile.gun);bound.put(model,selected);}
            catch(RuntimeException failure) {rejected.put(model,selected);LogUtils.getLogger().error("Cannot attach Zeus source mesh "+selected.profile.gun,failure);}
        });
    }
    /** A retained animation instance can select a cold cosmetic display between ticks. */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void initializeZeusFirstPerson(RenderHandEvent event) {
        if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        var mc=Minecraft.getInstance();if(mc==null || mc.player==null)return;
        var stack=TaCZClientCompat.currentRenderedItem();if(stack==null)return;var gun=IGun.getIGunOrNull(stack);
        if(gun==null || !ZeusScopes.family(gun.getGunId(stack)))return;
        var held=mc.player.getMainHandItem();var heldGun=IGun.getIGunOrNull(held);
        if(heldGun==null || !gun.getGunId(stack).equals(heldGun.getGunId(held)))return;
        var display=TimelessAPI.getGunDisplay(stack).orElse(null);
        if(display==null || !bound.containsKey(display.getGunModel()))return;
        var renderer=IClientItemExtensions.of(stack.getItem()).getCustomRenderer();
        if(renderer instanceof AnimateGeoItemRenderer<?,?> animated && animated.needReInit(stack))
            animated.tryInit(stack,mc.player,event.getPartialTick());
    }
    public static void validate(Geometry data) {
        if(data==null || data.parts==null || data.parts.length==0)throw new IllegalArgumentException("No source mesh parts");
        String texture=data.texture;
        for(Part part:data.parts) {
            if(part==null || part.bone==null || part.bone.isBlank() || part.positions==null || part.normals==null || part.uv==null || part.triangles==null
                || part.positions.length==0 || part.triangles.length==0 || part.positions.length!=part.normals.length || part.positions.length!=part.uv.length)
                throw new IllegalArgumentException("Incomplete source mesh streams");
            if(part.render!=null)throw new IllegalArgumentException("This base-gun renderer has no particle/effect material route");
            if(part.texture!=null && !part.texture.isBlank()) {
                if(texture==null)texture=part.texture;
                else if(!texture.equals(part.texture))throw new IllegalArgumentException("Source textures must share the gun's display atlas");
            }
            for(int i=0;i<part.positions.length;i++){vector(part.positions[i],3);vector(part.normals[i],3);vector(part.uv[i],2);}
            for(int[] face:part.triangles) {
                if(face==null || face.length!=3)throw new IllegalArgumentException("Non-triangle source face");
                for(int index:face)if(index<0 || index>=part.positions.length)throw new IllegalArgumentException("Invalid source face index");
            }
        }
    }
    private static void vector(float[] values,int width) {
        if(values==null || values.length!=width)throw new IllegalArgumentException("Wrong source vector width");
        for(float value:values)if(!Float.isFinite(value))throw new IllegalArgumentException("Non-finite source vector");
    }
    public static void attach(BedrockAnimatedModel model,Geometry data) {
        Loaded second=loaded.stream().filter(x->x.profile.display.equals(DISPLAY)).findFirst().orElse(null);
        attach(model,data,second==null?null:second.effects,GUN);
    }
    private static void attach(BedrockAnimatedModel model,Geometry data,cn.ssjjdimension.client.DimensionParticles.Data effects,ResourceLocation gunId) {
        validate(data);Map<String,List<Part>> groups=new LinkedHashMap<>();
        for(Part part:data.parts) {
            if(model.getNode(part.bone)==null)throw new IllegalArgumentException("Missing source joint "+part.bone);
            groups.computeIfAbsent(part.bone,key->new ArrayList<>()).add(part);
        }
        lockRefitView(model);
        for(var entry:groups.entrySet()) {
            List<Part> parts=List.copyOf(entry.getValue());
            model.setFunctionalRenderer(entry.getKey(),node->(pose,consumer,context,light,overlay)->{
                for(Part part:parts)emit(part,pose,consumer,light,overlay);
                cn.ssjjdimension.client.DimensionParticleEffects.defer(model,entry.getKey(),pose,effects);
                for(var child:node.children)child.render(pose,context,consumer,light,overlay);
            });
        }
        ZeusNativeHands.install(model,gunId);
    }
    /** Keep the full source gun visible in the attachment workbench view. */
    public static void lockRefitView(BedrockAnimatedModel model) {
        if(!(model instanceof BedrockGunModel gun))return;
        try {
            var field=BedrockGunModel.class.getDeclaredField("refitAttachmentViewPath");field.setAccessible(true);
            @SuppressWarnings("unchecked") var paths=(Map<AttachmentType,List<BedrockPart>>)field.get(gun);
            var full=gun.getRefitAttachmentViewPath(AttachmentType.NONE);
            if(full==null || full.isEmpty())throw new IllegalArgumentException("Missing full-gun refit camera path");
            for(var type:AttachmentType.values())paths.put(type,full);
        } catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot establish Zeus Hand II refit view",failure);}
    }
    public static void emit(Part part,PoseStack pose,VertexConsumer consumer,int light,int overlay) {
        // NEW_ENTITY uses quads; duplicate the third corner to submit a degenerate quad.
        for(int[] face:part.triangles)for(int corner=0;corner<4;corner++) {
            int i=face[Math.min(corner,2)];float[] p=part.positions[i],n=part.normals[i],uv=part.uv[i];
            consumer.vertex(pose.last().pose(),p[0],p[1],p[2]).color(1f,1f,1f,1f).uv(uv[0],uv[1])
                .overlayCoords(overlay).uv2(light).normal(pose.last().normal(),n[0],n[1],n[2]).endVertex();
        }
    }
}
