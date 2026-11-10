package cn.ssjjparasite2.client;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Direct source triangles in explicit joint-local blocks, using TaCZ's consumer. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class SourceMeshRenderer {
    private static final ResourceLocation ID=new ResourceLocation("ssjjparasite2", "parasitic_sniper2");
    private static final ResourceLocation RESOURCE=new ResourceLocation("ssjjparasite2", "source_mesh.json");
    public static final class Part {
        public String bone;
        public float[][] positions, normals, uv;
        public int[][] triangles;
    }
    public static final class Geometry { public Part[] parts; }
    private static volatile Geometry geometry;
    private static final Map<String,Geometry> skins=new LinkedHashMap<>();
    private static final Map<BedrockAnimatedModel, Geometry> bound=new WeakHashMap<>();
    private static final Map<BedrockAnimatedModel, Geometry> rejected=new WeakHashMap<>();
    private SourceMeshRenderer() {}

    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT, bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientResources {
        @SubscribeEvent public static void registerSourceMeshes(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)SourceMeshRenderer::read);
        }
    }
    public static void read(ResourceManager resources) {
        geometry=null; skins.clear();
        try (var stream=resources.open(RESOURCE);
             var reader=new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Geometry fresh=new Gson().fromJson(reader, Geometry.class);
            validate(fresh);
            for(String skin:new String[]{"frost","ship"}) {
                try(var input=resources.open(new ResourceLocation("ssjjparasite2","source_mesh_"+skin+".json"));var text=new InputStreamReader(input,StandardCharsets.UTF_8)) {
                    Geometry data=new Gson().fromJson(text,Geometry.class);validate(data);skins.put(skin,data);
                }
            }
            for(String gun:new String[]{"parasitic_sniper","parasitic_sniper1"}) {
                try(var input=resources.open(new ResourceLocation("ssjjparasite2","source_mesh_"+gun+".json"));var text=new InputStreamReader(input,StandardCharsets.UTF_8)) {
                    Geometry data=new Gson().fromJson(text,Geometry.class);validate(data);skins.put(gun,data);
                }
            }
            geometry=fresh;
            LogUtils.getLogger().info("PARASITE2_SOURCE_REBUILD mesh loaded from new raw-asset conversion");
        } catch (Exception failure) {
            LogUtils.getLogger().error("Cannot read rebuilt Parasite II geometry", failure);
        }
    }
    @SubscribeEvent public static void onClientTick(TickEvent.ClientTickEvent event) {
        Geometry current=geometry;
        if (event.phase!=TickEvent.Phase.END || current==null) return;
        for(String gun:new String[]{"parasitic_sniper","parasitic_sniper1","parasitic_sniper2"})
        for(String skin:new String[]{"base","frost","ship"}) {
            Geometry selected=skin.equals("base")?(gun.equals("parasitic_sniper2")?current:skins.get(gun)):skins.get(skin);
            if(selected==null) continue;
            String display=gun+(skin.equals("base")?"":"_"+skin)+"_display";
            TimelessAPI.getGunDisplay(new ResourceLocation("ssjjparasite2",display),new ResourceLocation("ssjjparasite2",gun)).ifPresent(index->{
                BedrockAnimatedModel model=index.getGunModel();
                if(model==null || bound.get(model)==selected || rejected.get(model)==selected)return;
                try{attach(model,selected);bound.put(model,selected);}
                catch(RuntimeException e){rejected.put(model,selected);LogUtils.getLogger().error("Cannot attach Parasite model",e);}
            });
        }
    }
    /** Every slot uses the established full-gun refit camera. TaCZ's field is not obfuscated. */
    public static void lockRefitView(BedrockAnimatedModel model) {
        if(!(model instanceof com.tacz.guns.client.model.BedrockGunModel gun))return;
        try {
            var field=com.tacz.guns.client.model.BedrockGunModel.class.getDeclaredField("refitAttachmentViewPath");field.setAccessible(true);
            @SuppressWarnings("unchecked") var paths=(java.util.Map<com.tacz.guns.api.item.attachment.AttachmentType,java.util.List<com.tacz.guns.client.model.bedrock.BedrockPart>>)field.get(gun);
            var full=gun.getRefitAttachmentViewPath(com.tacz.guns.api.item.attachment.AttachmentType.NONE);
            if(full==null||full.isEmpty())throw new IllegalArgumentException("Missing full-gun refit view");
            for(var type:com.tacz.guns.api.item.attachment.AttachmentType.values())paths.put(type,full);
        } catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot lock Parasite refit view",e);}
    }
    public static void validate(Geometry data) {
        if (data==null || data.parts==null || data.parts.length==0) throw new IllegalArgumentException("No source mesh parts");
        for (Part part:data.parts) {
            if (part==null || part.bone==null || part.positions==null || part.normals==null || part.uv==null
                    || part.triangles==null || part.positions.length==0 || part.triangles.length==0
                    || part.positions.length!=part.normals.length || part.positions.length!=part.uv.length)
                throw new IllegalArgumentException("Incomplete source mesh streams");
            for (int i=0;i<part.positions.length;i++) {
                vector(part.positions[i],3); vector(part.normals[i],3); vector(part.uv[i],2);
            }
            for (int[] face:part.triangles) {
                if (face==null || face.length!=3) throw new IllegalArgumentException("Non-triangle source mesh face");
                for (int vertex:face) if (vertex<0 || vertex>=part.positions.length) throw new IllegalArgumentException("Invalid source mesh index");
            }
        }
    }
    private static void vector(float[] values,int size) {
        if (values==null || values.length!=size) throw new IllegalArgumentException("Invalid source vector width");
        for (float value:values) if (!Float.isFinite(value)) throw new IllegalArgumentException("Non-finite source vector");
    }
    /** Attach geometry only; animation, hands, textures and draw submission belong to TaCZ. */
    public static void attach(BedrockAnimatedModel model,Geometry data) {
        validate(data);
        lockRefitView(model);
        Map<String,List<Part>> groups=new LinkedHashMap<>();
        for (Part part:data.parts) {
            if (model.getNode(part.bone)==null) throw new IllegalArgumentException("Absent source joint "+part.bone);
            groups.computeIfAbsent(part.bone,key->new ArrayList<>()).add(part);
        }
        for (var entry:groups.entrySet()) {
            List<Part> parts=List.copyOf(entry.getValue());
            model.setFunctionalRenderer(entry.getKey(),node->(pose,consumer,context,light,overlay)->{
                for (Part part:parts) emit(part,pose,consumer,light,overlay);
                ParasiteEffectsClient.defer(model,entry.getKey(),pose,context);
                for (var child:node.children) child.render(pose,context,consumer,light,overlay);
            });
        }
        model.setFunctionalRenderer("root", node->(pose,consumer,context,light,overlay)->{
            var mc=net.minecraft.client.Minecraft.getInstance();
            boolean scoped=context.firstPerson() && mc!=null && mc.player!=null && mc.options.getCameraType().isFirstPerson()
                && com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(mc.getFrameTime())>.8f;
            if(!scoped) for(var child:node.children) child.render(pose,context,consumer,light,overlay);
        });
        NativeHandView.installHandFits(model);
    }
    public static void emit(Part part,PoseStack transform,VertexConsumer consumer,int light,int overlay) {
        for (int[] face:part.triangles) {
            // Standard Minecraft quad indices: ABC is the source triangle;
            // repeating C makes the second indexed triangle degenerate.
            for (int corner=0;corner<4;corner++) {
                int index=face[Math.min(corner,2)];
                float[] p=part.positions[index],n=part.normals[index],uv=part.uv[index];
                consumer.vertex(transform.last().pose(),p[0],p[1],p[2])
                    .color(1f,1f,1f,1f).uv(uv[0],uv[1]).overlayCoords(overlay).uv2(light)
                    .normal(transform.last().normal(),n[0],n[1],n[2]).endVertex();
            }
        }
    }
}
