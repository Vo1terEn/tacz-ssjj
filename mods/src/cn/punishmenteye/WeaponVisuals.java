package cn.punishmenteye;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID,value=Dist.CLIENT)
public final class WeaponVisuals {
    static final ResourceLocation GUN=new ResourceLocation("eyeofpunishment","notdistinguish");
    private static final ResourceLocation DATA=new ResourceLocation("eyeofpunishment","rig/weapon_mesh.json");
    private static final Set<BedrockAnimatedModel> installed=Collections.newSetFromMap(new WeakHashMap<>());
    static volatile Definition definition;
    static volatile Part scope;
    public static final class Definition { public Part[] parts; public int triangles; }
    public static final class Part { public String bone; public float[][] vertices,uv,normals; public int[][] faces; }

    @Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class Resources {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)WeaponVisuals::reload);
        }
    }
    static void reload(ResourceManager resources) {
        installed.clear();definition=null;scope=null;
        try(var stream=resources.open(DATA);var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            Definition next=new Gson().fromJson(reader,Definition.class);
            if(next.parts==null||next.triangles<=0)throw new IllegalArgumentException("Empty weapon mesh");
            definition=next;
            MuzzleVisuals.reload(resources);
            SkinVisuals.reload(resources);
            try(var fx=resources.open(new ResourceLocation("eyeofpunishment","rig/scope_t129.json"));var fxReader=new InputStreamReader(fx,StandardCharsets.UTF_8)){
                scope=new Gson().fromJson(fxReader,Part.class);
            }
        } catch(Exception e) { LogUtils.getLogger().error("Cannot load Eye of Punishment weapon mesh",e); }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||Minecraft.getInstance().level==null)return;
        TimelessAPI.getClientGunIndex(GUN).ifPresent(index->SkinVisuals.installBase(index.getDefaultDisplay().getGunModel(),definition));
        SkinVisuals.install();
    }
    public static void install(BedrockAnimatedModel model,Definition data) { install(model,data,null); }
    public static void install(BedrockAnimatedModel model,Definition data,java.util.function.BiConsumer<Part,Matrix4f> poseObserver) {
        installBase(model,data,poseObserver);
    }
    /** Native traversal sink; end receives null if this draw never reached the receiver. */
    public interface SkinRenderSink {
        void body(Part mesh,PoseStack pose,VertexConsumer nativeOut,ItemDisplayContext context,int light,int overlay);
        void end(PoseStack receiverPose,PoseStack muzzlePose,ItemDisplayContext context,int light,int overlay);
    }
    public static void installSkin(BedrockAnimatedModel model,Definition data,String skin) {
        installSkin(model,data,skin,SkinVisuals.sink(skin));
    }
    public static void installSkin(BedrockAnimatedModel model,Definition data,String skin,SkinRenderSink sink) {
        if(model==null||data==null||installed.contains(model)||model.getNode("detail_007")==null)return;
        Objects.requireNonNull(sink,"skin render sink");
        var tail=model.getNode("skin_fx_end");
        var roots=model.getShouldRender();
        if(tail==null&&skin.equals("base")) {
            // Native setFunctionalRenderer creates an unattached empty node.
            // Appending it preserves every approved geometry/action resource.
            model.setFunctionalRenderer("skin_fx_end",part->null);
            tail=model.getNode("skin_fx_end");roots.add(tail);
        }
        if(tail==null||roots.isEmpty()||roots.get(roots.size()-1)!=tail||!tail.cubes.isEmpty())
            throw new IllegalArgumentException("Skin requires final empty root skin_fx_end: "+skin);
        final class Frame {
            PoseStack receiver,muzzle;
            ItemDisplayContext context;
            int light,overlay;
        }
        var frame=new Frame();
        for(Part mesh:data.parts) {
            var bone=model.getNode(mesh.bone);
            if(bone==null)throw new IllegalArgumentException("Missing skin mesh bone "+mesh.bone);
            bone.cubes.clear();
            model.setFunctionalRenderer(mesh.bone,part->(pose,out,context,light,overlay)->{
                if(mesh.bone.equals("receiver")) {
                    frame.receiver=copyPose(pose);frame.context=context;frame.light=light;frame.overlay=overlay;
                }
                sink.body(mesh,pose,out,context,light,overlay);
            });
        }
        var muzzle=model.getNode("muzzle_flash");
        if(!(muzzle instanceof FunctionalBedrockPart nativeMuzzle)||!muzzle.cubes.isEmpty()||!muzzle.children.isEmpty())
            throw new IllegalArgumentException("Missing empty native muzzle_flash socket: "+skin);
        var originalMuzzle=nativeMuzzle.functionalRenderer;
        model.setFunctionalRenderer("muzzle_flash",part->{
            var original=originalMuzzle==null?null:originalMuzzle.apply(part);
            return (pose,out,context,light,overlay)->{
                frame.muzzle=copyPose(pose);
                // Keep the base display's native flash/delegate renderer intact.
                if(original!=null)original.render(pose,out,context,light,overlay);
            };
        });
        model.setFunctionalRenderer("skin_fx_end",part->(pose,out,context,light,overlay)->{
            try {
                sink.end(frame.receiver,frame.muzzle,frame.receiver==null?context:frame.context,
                    frame.receiver==null?light:frame.light,frame.receiver==null?overlay:frame.overlay);
            } finally { frame.receiver=null;frame.muzzle=null;frame.context=null; }
        });
        installed.add(model);
    }
    public static PoseStack copyPose(PoseStack pose) {
        var saved=new PoseStack();saved.last().pose().set(pose.last().pose());saved.last().normal().set(pose.last().normal());return saved;
    }
    public static PoseStack effectPose(String anchor,PoseStack receiver,PoseStack muzzle) {
        return switch(anchor) {case "receiver"->receiver;case "muzzle_flash"->muzzle;default->throw new IllegalArgumentException("Unknown effect socket: "+anchor);};
    }
    private static void installBase(BedrockAnimatedModel model,Definition data,java.util.function.BiConsumer<Part,Matrix4f> poseObserver) {
        if(model==null||data==null||installed.contains(model)||model.getNode("detail_007")==null)return;
        for(Part mesh:data.parts) {
            var bone=model.getNode(mesh.bone);
            if(bone==null)throw new IllegalArgumentException("Missing mesh bone "+mesh.bone);
            bone.cubes.clear();
            model.setFunctionalRenderer(mesh.bone,part->(pose,out,context,light,overlay)->{
                float skillAge=mesh.bone.equals("receiver")?SkillVisualState.activeAgeFor(context):-1;
                if(skillAge>=0)MuzzleVisuals.render(pose,overlay,mesh,light,skillAge);
                else render(mesh,pose,out,light,overlay);
                if(scope!=null&&mesh.bone.equals("receiver"))ScopeVisuals.render(scope,pose,overlay);
                if(poseObserver!=null)poseObserver.accept(mesh,new Matrix4f(pose.last().pose()));
            });
        }
        installed.add(model);
    }
    public static void render(Part mesh,PoseStack pose,VertexConsumer out,int light,int overlay) {
        for(int[] face:mesh.faces) {
            // One triangle as a degenerate quad. No coincident reverse-side copy.
            for(int j:new int[]{0,2,1,1}) {
                int i=face[j];float[] p=mesh.vertices[i],uv=mesh.uv[i],n=mesh.normals[i];
                Vector3f normal=new Vector3f(n[0],-n[1],n[2]).mul(pose.last().normal()).normalize();
                out.vertex(pose.last().pose(),p[0]/16,-p[1]/16,p[2]/16).color(1f,1f,1f,1f)
                    .uv(uv[0],uv[1]).overlayCoords(overlay).uv2(light).normal(normal.x,normal.y,normal.z).endVertex();
            }
        }
    }
}
