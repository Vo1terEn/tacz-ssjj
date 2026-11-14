package cn.ssjjzeus.client;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.client.animation.*;
import com.tacz.guns.client.model.*;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.TransformScale;
import com.tacz.guns.client.resource.pojo.animation.bedrock.*;
import com.tacz.guns.client.resource.pojo.model.*;
import com.tacz.guns.client.resource.serialize.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.joml.Matrix4f;

/** A stationary phantom needs the original gun, not the complete animated item renderer.
 * Bake a private native model once, retain every original triangle/UV, and reuse its VBO.
 * Never mutates the live player's model and never invokes gun particle/attachment delegates. */
public final class ZeusPhantomWeapon {
    private record Assets(ZeusMeshRenderer.Geometry mesh,BedrockModelPOJO model,List<ObjectAnimation> clips,
                          TransformScale scale,Map<String,ResourceLocation> textures) {}
    private static final Gson ANIMATIONS=new GsonBuilder().registerTypeAdapter(Vector3f.class,new Vector3fSerializer())
        .registerTypeAdapter(AnimationKeyframes.class,new AnimationKeyframesSerializer()).create();
    private static volatile Assets assets;
    private static volatile VertexBuffer buffer;
    private static boolean fallbackAttempted,missingShaderReported;
    private static final Map<String,RenderType> materials=new HashMap<>();
    private ZeusPhantomWeapon() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ZeusPhantomWeapon::reload);
        }
    }
    private static JsonObject json(ResourceManager resources,String path)throws IOException {
        try(var stream=resources.open(new ResourceLocation("ssjj_zeus",path));var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
    public static void reload(ResourceManager resources) {
        assets=null;fallbackAttempted=false;missingShaderReported=false;clear();
        try {
            var mesh=new Gson().fromJson(json(resources,"source_mesh.json"),ZeusMeshRenderer.Geometry.class);ZeusMeshRenderer.validate(mesh);
            var model=ClientAssetsManager.GSON.fromJson(json(resources,"geo_models/gun/hand_of_zeuss2_geo.json"),BedrockModelPOJO.class);
            var all=json(resources,"animations/hand_of_zeuss2.animation.json");var only=new JsonObject();var clips=new JsonObject();
            clips.add("static_idle",all.getAsJsonObject("animations").get("static_idle"));only.add("animations",clips);
            var prototypes=Animations.createAnimationFromBedrock(ANIMATIONS.fromJson(only,BedrockAnimationFile.class));
            Map<String,ResourceLocation> textures=new LinkedHashMap<>();TransformScale scale=null;
            for(String suffix:List.of("","_lava","_galaxy")) {
                String profile="hand_of_zeuss2"+suffix;var display=json(resources,"display/guns/"+profile+"_display.json");
                var texture=new ResourceLocation(display.get("texture").getAsString());
                textures.put(profile,new ResourceLocation(texture.getNamespace(),"textures/"+texture.getPath()+".png"));
                if(suffix.isEmpty())scale=ClientAssetsManager.GSON.fromJson(display.getAsJsonObject("transform").get("scale"),TransformScale.class);
            }
            assets=new Assets(mesh,model,List.copyOf(prototypes),scale,Map.copyOf(textures));
            LogUtils.getLogger().info("ZEUS_PHANTOM loaded original gun mesh, static pose and three textures");
        }catch(Exception failure){LogUtils.getLogger().error("Cannot load original Zeus phantom weapon",failure);}
    }
    /** Render-thread lifetime: detach references now and close the old GPU object on its owner thread. */
    public static void clear() {
        VertexBuffer previous=buffer;buffer=null;
        Runnable release=()->{if(previous!=null)previous.close();materials.clear();};
        if(RenderSystem.isOnRenderThread())release.run();else RenderSystem.recordRenderCall(release::run);
    }
    public static int bake(ZeusMeshRenderer.Geometry mesh,BedrockModelPOJO geometry,List<ObjectAnimation> clips,TransformScale scale,VertexConsumer out) {
        ZeusMeshRenderer.validate(mesh);var model=new BedrockGunModel(geometry,BedrockVersion.NEW);
        var controller=new AnimationController(clips,model);controller.runAnimation(0,"static_idle",ObjectAnimation.PlayType.LOOP,0);
        controller.getAnimation(0).pause();controller.getAnimation(0).setProgressNs(0);model.cleanAnimationTransform();controller.update();
        PoseStack item=new PoseStack();item.translate(-.5,-.5,-.5);
        // The installed 1.2 implementation is newer than the compile-time named
        // TaCZ API. Resolve it once during baking, never on the render hot path.
        try {Class.forName("com.tacz.guns.client.renderer.item.GunItemTransforms").getMethod("apply",ItemDisplayContext.class,TransformScale.class,BedrockGunModel.class,PoseStack.class)
                .invoke(null,ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,scale,model,item);}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Missing native phantom third-person transform",failure);}
        int vertices=0;
        for(var part:mesh.parts) {
            var node=model.getNode(part.bone);if(node==null)throw new IllegalArgumentException("Missing phantom source joint "+part.bone);
            var path=new ArrayList<BedrockPart>();for(var n=node;n!=null;n=n.getParent()){path.add(n);if(path.size()>64)throw new IllegalArgumentException("Phantom joint cycle");}
            item.pushPose();for(int i=path.size()-1;i>=0;i--)path.get(i).translateAndRotateAndScale(item);
            ZeusMeshRenderer.emit(part,item,out,15728880,OverlayTexture.NO_OVERLAY);item.popPose();vertices+=part.triangles.length*4;
        }
        return vertices;
    }
    /** Match immediate entity rendering: emitted local vertices pass through
     * both the event pose and the global camera/model-view matrix. */
    public static Matrix4f modelView(PoseStack pose) {
        return new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
    }
    public static void draw(PoseStack pose,String profile) {
        RenderSystem.assertOnRenderThread();var source=assets;
        // A late-mounted gunpack must not leave this optional listener silently
        // empty. At most one retry per resource generation; never per phantom.
        if(source==null&&!fallbackAttempted) {
            LogUtils.getLogger().warn("ZEUS_PHANTOM resources were not ready; retrying current client resources once");
            reload(Minecraft.getInstance().getResourceManager());fallbackAttempted=true;source=assets;
        }
        if(source==null)return;
        if(buffer==null) {
            var builder=Tesselator.getInstance().getBuilder();builder.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.NEW_ENTITY);
            int vertices=bake(source.mesh(),source.model(),source.clips(),source.scale(),builder);
            buffer=new VertexBuffer(VertexBuffer.Usage.STATIC);buffer.bind();buffer.upload(builder.end());VertexBuffer.unbind();
            LogUtils.getLogger().info("ZEUS_PHANTOM uploaded cached original gun: {} vertices",vertices);
        }
        var textures=source.textures();RenderType type=materials.computeIfAbsent(profile,key->RenderType.entityTranslucentEmissive(textures.getOrDefault(key,textures.get("hand_of_zeuss2"))));
        type.setupRenderState();
        try {
            var shader=RenderSystem.getShader();
            if(shader==null){if(!missingShaderReported){missingShaderReported=true;LogUtils.getLogger().error("ZEUS_PHANTOM original gun shader is unavailable");}return;}
            buffer.bind();buffer.drawWithShader(modelView(pose),RenderSystem.getProjectionMatrix(),shader);
        }
        finally {VertexBuffer.unbind();type.clearRenderState();}
    }
}
