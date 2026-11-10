package cn.judgfire;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import com.tacz.guns.client.model.IFunctionalRenderer;
import com.tacz.guns.compat.oculus.OculusCompat;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
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

/** E009's real wing mesh and UV2 overlay; isolated translucent batches follow the gun pose. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID, value = Dist.CLIENT)
public final class BodyEffects {
  private static final ResourceLocation DATA =
      new ResourceLocation("judgfire", "fx/body_e009.json");
  private static final ResourceLocation PIXEL_SCOPE =
      new ResourceLocation("judgfire", "flame_crown_scope");
  private static final Set<BedrockGunModel> INSTALLED =
      Collections.newSetFromMap(new WeakHashMap<>());
  private static final MultiBufferSource.BufferSource BUFFERS =
      MultiBufferSource.immediate(new BufferBuilder(262144));
  private static volatile Definition definition;
  private static volatile Definition qinglong, frozen, lightning;
  private static final ResourceLocation QINGLONG_DATA =
      new ResourceLocation("judgfire", "fx/body_qinglong.json");

  static final class Definition {
    double period_seconds;
    Group[] groups;
    Emitter[] emitters;

    void validate() {
      if (groups == null || emitters == null || period_seconds <= 0)
        throw new IllegalArgumentException("Invalid body effect definition");
      for (Group g : groups) {
        if (g.binding == null) throw new IllegalArgumentException("Missing body effect binding");
        OriginalMotion.validate(g.binding, g.vertices.length);
      }
      for (Emitter e : emitters) {
        if (e.binding == null || e.binding.bone < 0)
          throw new IllegalArgumentException("Missing emitter attachment");
        OriginalMotion.validate(e.binding, 1);
      }
    }
  }

  static final class Group {
    String name, texture;
    float[][] vertices, uv, uv1;
    int[][] faces;
    int frames, cols, rows;
    float alpha;
    boolean pulse;
    BodyEffectBinding binding;
  }

  static final class Emitter {
    String texture;
    float[] position;
    float size, alpha;
    boolean embers;
    BodyEffectBinding binding;
  }

  @Mod.EventBusSubscriber(
      modid = JudgmentFlame.MODID,
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Resources {
    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener((ResourceManagerReloadListener) BodyEffects::reload);
    }
  }

  private static void reload(ResourceManager manager) {
    OriginalMotion.reload(manager);
    JudgmentBodyFx.reload(manager);
    QinglongVisuals.reload(manager);
    FrozenVisuals.reload(manager);
    LightningVisuals.reload(manager);
    try (var input = manager.open(QINGLONG_DATA);
        var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      qinglong = new Gson().fromJson(reader, Definition.class);
      qinglong.validate();
    } catch (Exception error) {
      qinglong = null;
      LogUtils.getLogger().error("Cannot load Qinglong body effects", error);
    }
    try (var input = manager.open(new ResourceLocation("judgfire", "fx/body_frozen.json"));
        var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      frozen = new Gson().fromJson(reader, Definition.class);
      frozen.validate();
    } catch (Exception error) {
      frozen = null;
      LogUtils.getLogger().error("Cannot load Frozen body effects", error);
    }
    try (var input = manager.open(new ResourceLocation("judgfire", "fx/body_lightning.json"));
        var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      lightning = new Gson().fromJson(reader, Definition.class);
      lightning.validate();
    } catch (Exception error) {
      lightning = null;
      LogUtils.getLogger().error("Cannot load Lightning body effects", error);
    }
    try (var input = manager.open(DATA);
        var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      Definition next = new Gson().fromJson(reader, Definition.class);
      next.validate();
      definition = next;
    } catch (Exception error) {
      definition = null;
      LogUtils.getLogger().error("Cannot load selected E009 body effect", error);
    }
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().level == null) return;
    AttachmentAppearance.tick();
    TimelessAPI.getClientGunIndex(JudgmentFlame.GUN)
        .ifPresent(
            index -> {
              var display = index.getDefaultDisplay();
              install(display.getGunModel());
              var lod = display.getLodModel();
              if (lod != null) install(lod.getLeft());
            });
    TimelessAPI.getGunDisplay(WeaponSkins.DISPLAY, JudgmentFlame.GUN)
        .ifPresent(display -> install(display.getGunModel()));
    TimelessAPI.getGunDisplay(WeaponSkins.BASE_VIDEO_DISPLAY, JudgmentFlame.GUN)
        .ifPresent(display -> install(display.getGunModel()));
    TimelessAPI.getGunDisplay(WeaponSkins.FROZEN_DISPLAY, JudgmentFlame.GUN)
        .ifPresent(display -> install(display.getGunModel()));
    TimelessAPI.getGunDisplay(WeaponSkins.LIGHTNING_DISPLAY, JudgmentFlame.GUN)
        .ifPresent(display -> install(display.getGunModel()));
  }

  private static void install(BedrockGunModel model) {
    if (model == null || INSTALLED.contains(model)) return;
    var anchor = model.getNode("e009_vfx_anchor");
    var root = model.getRootNode();
    if (anchor == null || root == null) return;
    AttachmentAppearance.gun(model);
    OriginalMotion.install(model);
    // Capture at the exact callback that emits the animated skin. A sibling FX
    // anchor is not authoritative if a resource pack or animation moves either
    // node. The deferred pass receives an immutable copy of this same skin pose.
    var skinNode=model.getNode("source_skin_anchor");
    if(skinNode instanceof FunctionalBedrockPart skinPart){
      var original=skinPart.functionalRenderer;
      model.setFunctionalRenderer("source_skin_anchor",part->afterGeometry(
          original==null?null:original.apply(part),
          (pose,out,context,light,overlay)->queueSkinEffects(model,pose,context)));
    }
    // BedrockAnimatedModel preallocates functional parts for every bone on load.
    model.setFunctionalRenderer(
        "e009_vfx_anchor",
        part ->
            (pose, out, context, light, overlay) -> {
              if (context == ItemDisplayContext.GUI
                  || context == ItemDisplayContext.GROUND
                  || context == ItemDisplayContext.FIXED
                  || OculusCompat.isRenderShadow()) return;
              ClientEffects.capture(model, pose, context);
            });
    INSTALLED.add(model);
  }

  static IFunctionalRenderer afterGeometry(IFunctionalRenderer geometry,IFunctionalRenderer effects){
    return (pose,out,context,light,overlay)->{
      if(geometry!=null)geometry.render(pose,out,context,light,overlay);
      effects.render(pose,out,context,light,overlay);
    };
  }
  static boolean bodyContext(ItemDisplayContext context){
    return context!=ItemDisplayContext.GUI&&context!=ItemDisplayContext.GROUND&&context!=ItemDisplayContext.FIXED;
  }
  private static void queueSkinEffects(BedrockGunModel model,com.mojang.blaze3d.vertex.PoseStack pose,ItemDisplayContext context){
    if(!bodyContext(context)||OculusCompat.isRenderShadow())return;
    var mc=Minecraft.getInstance();
    if(context.firstPerson()&&mc.player!=null&&IClientPlayerGunOperator.fromLocalPlayer(mc.player).getClientAimingProgress(mc.getFrameTime())>.8f)return;
    Matrix4f captured=new Matrix4f(pose.last().pose());
    if(!cn.fusionarsenal.firefx.FireRenderPose.drawable(captured,"Judgment source_skin_anchor "+context))return;
    var stack=model.getCurrentGunItem();var gun=stack==null?null:IGun.getIGunOrNull(stack);
    boolean pixel=gun!=null&&PIXEL_SCOPE.equals(gun.getAttachmentId(stack,AttachmentType.SCOPE));
    var frame=OriginalMotion.frame(model);
    int skin=model.getNode("lightning_skin_anchor")!=null?3:model.getNode("frozen_skin_anchor")!=null?2:model.getNode("qinglong_skin_anchor")!=null?1:0;
    model.delegateRender((p,v,c,l,o)->JudgmentBodyFx.draw(model,captured,pixel,frame,skin,context));
  }

}
