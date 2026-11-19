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
import net.minecraft.client.renderer.ShaderInstance;
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
    private static boolean batching;
    private static RenderType batchType;
    private static ShaderInstance batchShader;
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
        Runnable release=()->{endBatch();if(previous!=null)previous.close();materials.clear();};
        if(RenderSystem.isOnRenderThread())release.run();else RenderSystem.recordRenderCall(release::run);
    }
    public static int bake(ZeusMeshRenderer.Geometry mesh,BedrockModelPOJO geometry,List<ObjectAnimation> clips,TransformScale scale,VertexConsumer out) {
        ZeusMeshRenderer.validate(mesh);var model=new BedrockGunModel(geometry,BedrockVersion.NEW);
        var controller=new AnimationController(clips,model);controller.runAnimation(0,"static_idle",ObjectAnimation.PlayType.LOOP,0);
        controller.getAnimation(0).pause();controller.getAnimation(0).setProgressNs(0);model.cleanAnimationTransform();controller.update();
        PoseStack item=new PoseStack();item.translate(-.5,-.5,-.5);
        cn.ssjjcompat.NativeGunItemTransforms.apply(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,scale,model,item);
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
    /** Retain native shader/material state across adjacent identical profiles.
     * Draw order, vertex count, textures and every per-phantom matrix stay intact. */
    public static void beginBatch() {RenderSystem.assertOnRenderThread();endBatch();batching=true;}
    public static void endBatch() {
        if(batchShader!=null)batchShader.clear();
        if(batchType!=null){VertexBuffer.unbind();batchType.clearRenderState();}
        batchShader=null;batchType=null;batching=false;
    }
    /** The NEW_ENTITY/QUADS uniforms from native VertexBuffer._drawWithShader.
     * Only ModelViewMat changes between adjacent instances of the same material. */
    private static void prepare(ShaderInstance shader,Matrix4f view) {
        for(int i=0;i<12;i++)shader.setSampler("Sampler"+i,RenderSystem.getShaderTexture(i));
        if(shader.MODEL_VIEW_MATRIX!=null)shader.MODEL_VIEW_MATRIX.set(view);
        if(shader.PROJECTION_MATRIX!=null)shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        if(shader.INVERSE_VIEW_ROTATION_MATRIX!=null)shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        if(shader.COLOR_MODULATOR!=null)shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        if(shader.GLINT_ALPHA!=null)shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        if(shader.FOG_START!=null)shader.FOG_START.set(RenderSystem.getShaderFogStart());
        if(shader.FOG_END!=null)shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        if(shader.FOG_COLOR!=null)shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        if(shader.FOG_SHAPE!=null)shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        if(shader.TEXTURE_MATRIX!=null)shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        if(shader.GAME_TIME!=null)shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        if(shader.SCREEN_SIZE!=null){var window=Minecraft.getInstance().getWindow();shader.SCREEN_SIZE.set((float)window.getWidth(),(float)window.getHeight());}
        RenderSystem.setupShaderLights(shader);shader.apply();
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
        if(batching) {
            var view=modelView(pose);
            if(batchType!=type) {
                endBatch();batching=true;type.setupRenderState();batchType=type;
                batchShader=RenderSystem.getShader();
                if(batchShader==null){endBatch();batching=true;return;}
                // A shaderpack ShaderInstance subclass can compute additional
                // per-object uniforms in apply(). Keep its native per-draw path.
                if(batchShader.getClass()!=ShaderInstance.class) {
                    endBatch();try{draw(pose,profile);}finally{batching=true;}return;
                }
                buffer.bind();prepare(batchShader,view);
            } else if(batchShader.MODEL_VIEW_MATRIX!=null) {
                batchShader.MODEL_VIEW_MATRIX.set(view);batchShader.MODEL_VIEW_MATRIX.upload();
            }
            buffer.draw();return;
        }
        type.setupRenderState();
        try {
            var shader=RenderSystem.getShader();
            if(shader==null){if(!missingShaderReported){missingShaderReported=true;LogUtils.getLogger().error("ZEUS_PHANTOM original gun shader is unavailable");}return;}
            buffer.bind();buffer.drawWithShader(modelView(pose),RenderSystem.getProjectionMatrix(),shader);
        }
        finally {VertexBuffer.unbind();type.clearRenderState();}
    }
}
