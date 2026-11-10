package cn.judgfire;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import java.util.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/** Server-acknowledged skill animation; no predicted damage or local timers for gameplay. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID, value = Dist.CLIENT)
public final class BurstClient {
  public static final KeyMapping KEY =
      new KeyMapping(
          "key.judgfire.burst",
          KeyConflictContext.IN_GAME,
          InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_R,
          "key.categories.judgfire");

  @Mod.EventBusSubscriber(
      modid = JudgmentFlame.MODID,
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Keys {
    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
      event.register(KEY);
    }
  }

  private record Visible(
      int shooter, UUID weapon, long until, long started, Object level, int skin) {}

  private static final Map<Integer, Visible> VISIBLE = new HashMap<>();
  private static final BurstAnimation animation = new BurstAnimation();
  private static UUID animatedWeapon;
  private static Object animationLevel;
  private static final MultiBufferSource.BufferSource BUFFERS =
      MultiBufferSource.immediate(new BufferBuilder(32768));
  private static final ResourceLocation
      WAVE = new ResourceLocation("judgfire", "textures/fx/burst_e010_wave.png"),
      RING = new ResourceLocation("judgfire", "textures/fx/crown_original_ring.png"),
      FIRE = new ResourceLocation("judgfire", "textures/fx/crown_original_fire.png");

  @SubscribeEvent(priority = EventPriority.HIGHEST)
  public static void key(InputEvent.Key event) {
    var mc = Minecraft.getInstance();
    if (event.getAction() != GLFW.GLFW_PRESS
        || mc.screen != null
        || mc.player == null
        || mc.player.isSpectator()
        || !KEY.matches(event.getKey(), event.getScanCode())) return;
    var stack = mc.player.getMainHandItem();
    if (!JudgmentFlame.burst(stack) || !BurstRuntime.loaded(stack) || BurstStun.stunned(mc.player))
      return;
    var tag = stack.getTag();
    if (tag != null && tag.hasUUID(EmperorCrown.ID))
      JudgmentFlame.CHANNEL.sendToServer(
          new JudgmentFlame.BurstRequest(tag.getUUID(EmperorCrown.ID)));
  }

  public static void state(JudgmentFlame.BurstState packet) {
    BurstHud.state(packet);
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    long now = System.nanoTime();
    var old = VISIBLE.get(packet.shooter());
    long started =
        packet.started()
            ? now
            : old != null
                ? old.started
                : now - (BurstTiming.DURATION - packet.remaining()) * 50_000_000L;
    if (packet.remaining() > 0)
      VISIBLE.put(
          packet.shooter(),
          new Visible(
              packet.shooter(),
              packet.weapon(),
              now + packet.remaining() * 50_000_000L,
              started,
              mc.level,
              packet.skin()));
    else VISIBLE.remove(packet.shooter());
    if (!packet.started() || mc.player == null || packet.shooter() != mc.player.getId()) return;
    var stack = mc.player.getMainHandItem();
    var tag = stack.getTag();
    if (!JudgmentFlame.burst(stack)
        || tag == null
        || !tag.hasUUID(EmperorCrown.ID)
        || !packet.weapon().equals(tag.getUUID(EmperorCrown.ID))) return;
    TimelessAPI.getGunDisplay(stack)
        .ifPresent(
            display -> {
              var machine = display.getAnimationStateMachine();
              if (machine == null || !machine.isInitialized()) return;
              var context = machine.getContext();
              if (context != null
                  && context.getStateMachine() == machine
                  && context.hasAnimationPrototype("red_lotus_burst")) {
                // Native default state machine reserves these top parallel tracks. Use the
                // last one so shooting/idle root poses cannot overwrite the activation.
                animation.clear();
                int track = context.getTrack(0, 15);
                context.runAnimation("red_lotus_burst", track, false, 1, .10f);
                animation.capture(context, track);
                animatedWeapon = packet.weapon();
                animationLevel = mc.level;
              }
            });
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    var mc = Minecraft.getInstance();
    if (animation.present()) {
      var stack =
          mc.player == null
              ? net.minecraft.world.item.ItemStack.EMPTY
              : mc.player.getMainHandItem();
      var tag = stack.getTag();
      if (!animation.bound()
          || mc.level != animationLevel
          || !JudgmentFlame.burst(stack)
          || tag == null
          || !tag.hasUUID(EmperorCrown.ID)
          || !animatedWeapon.equals(tag.getUUID(EmperorCrown.ID))) {
        animation.clear();
        animatedWeapon = null;
        animationLevel = null;
      }
    }
    VISIBLE.values().removeIf(s -> s.level != mc.level || System.nanoTime() > s.until);
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    long now = System.nanoTime();
    var camera = event.getCamera();
    var pose = event.getPoseStack();
    Vec3 eye = camera.getPosition();
    pose.pushPose();
    pose.translate(-eye.x, -eye.y, -eye.z);
    for (var visible : VISIBLE.values()) {
      if (visible.level != mc.level || visible.until <= now) continue;
      var entity = mc.level.getEntity(visible.shooter);
      if (entity == null) continue;
      double age = (now - visible.started) / 1e9;
      Vec3 base = entity.getPosition(event.getPartialTick());
      if (age < 1.25) {
        // Original E010 chongjibo_xian, five columns by six rows.
        int frame = Math.min(29, (int) (age * 24));
        float u = (frame % 5) / 5f, v = (frame / 5) / 6f;
        pose.pushPose();
        pose.translate(base.x, base.y + .10, base.z);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        float radius = (float) (.35 + age * 3.5);
        quad(
            pose,
            LightningVisuals.choose(
                visible.skin(),
                WAVE,
                QinglongVisuals.WAVE,
                FrozenVisuals.WAVE,
                LightningVisuals.WAVE),
            radius,
            radius,
            visible.skin() >= 2 ? 0 : u,
            visible.skin() >= 2 ? 1 : u + .2f,
            visible.skin() >= 2 ? 0 : v,
            visible.skin() >= 2 ? 1 : v + 1f / 6,
            (float) (1 - age / 1.25));
        pose.popPose();
      }
      if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
      Vec3 crown = base.add(0, entity.getBbHeight() + .65, 0);
      for (int i = 0; i < 9; i++) {
        double angle = age * 1.4 + i * Math.PI * 2 / 9;
        Vec3 point =
            crown.add(Math.cos(angle) * .62, .1 + Math.sin(angle) * .08, Math.sin(angle) * .62);
        pose.pushPose();
        pose.translate(point.x, point.y, point.z);
        pose.mulPose(camera.rotation());
        int frame = ((int) (age * 20) + i * 2) % 16;
        float u = (frame % 4) / 4f, v = (frame / 4) / 4f;
        quad(
            pose,
            LightningVisuals.choose(
                visible.skin(),
                FIRE,
                QinglongVisuals.FIRE,
                FrozenVisuals.FIRE,
                LightningVisuals.FIRE),
            .25f,
            .35f,
            u,
            u + .25f,
            v,
            v + .25f,
            .9f);
        pose.popPose();
      }
      pose.pushPose();
      pose.translate(crown.x, crown.y, crown.z);
      pose.mulPose(camera.rotation());
      quad(
          pose,
          LightningVisuals.choose(
              visible.skin(),
              RING,
              QinglongVisuals.RING,
              FrozenVisuals.RING,
              LightningVisuals.RING),
          .78f,
          .25f,
          0,
          1,
          0,
          1,
          .9f);
      pose.popPose();
    }
    pose.popPose();
    BUFFERS.endBatch();
  }

  private static void quad(
      PoseStack pose,
      ResourceLocation texture,
      float x,
      float y,
      float u,
      float u1,
      float v,
      float v1,
      float alpha) {
    var out = BUFFERS.getBuffer(RenderType.entityTranslucentEmissive(texture));
    var m = pose.last().pose();
    vertex(out, m, -x, -y, u, v1, alpha);
    vertex(out, m, x, -y, u1, v1, alpha);
    vertex(out, m, x, y, u1, v, alpha);
    vertex(out, m, -x, y, u, v, alpha);
  }

  private static void vertex(
      VertexConsumer out, Matrix4f m, float x, float y, float u, float v, float alpha) {
    out.vertex(m, x, y, 0)
        .color(1f, 1f, 1f, alpha)
        .uv(u, v)
        .overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(15728880)
        .normal(0, 1, 0)
        .endVertex();
  }
}
