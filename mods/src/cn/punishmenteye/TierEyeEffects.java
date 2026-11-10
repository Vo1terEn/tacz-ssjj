package cn.punishmenteye;

import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** The source first/tier-one groups, without any second-stage E014 routing. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class TierEyeEffects {
  private record Effect(MuzzleGeometry.Definition data, String anchor) {}
  private record Style(Effect normal, Effect active, Effect fire) {}
  private record Shot(String tier, double start, double last) {}
  private static final Map<String, Style> STYLES = new HashMap<>();
  private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
  private static final Map<UUID, Shot> SHOTS = new HashMap<>();
  private static final MultiBufferSource.BufferSource BUFFERS =
      MultiBufferSource.immediate(new BufferBuilder(4096));
  // Current shared gun data is 660rpm. Allow one client tick of event jitter;
  // the serialized source system duration is not its firing stop window.
  private static final double SHOT_KEEPALIVE_TICKS = 20.0 * 60.0 / 660.0 + 1.0;
  private static ClientLevel level;
  private static boolean loaded;
  private TierEyeEffects() {}

  static void reset() {
    STYLES.clear(); TEXTURES.clear(); SHOTS.clear(); loaded = false; level = null;
  }

  static void load() {
    if (loaded) return;
    loaded = true;
    var resources = Minecraft.getInstance().getResourceManager();
    try {
      for (String tier : new String[]{"base", "tier1"}) {
        STYLES.put(tier, new Style(read(resources, tier, "normal"),
            read(resources, tier, "active"), read(resources, tier, "fire")));
      }
    } catch (Exception error) {
      STYLES.clear();
      LogUtils.getLogger().error("Cannot load original Eye tier particle definitions", error);
    }
  }

  private static Effect read(net.minecraft.server.packs.resources.ResourceManager resources,
      String tier, String state) throws java.io.IOException {
    var location = new ResourceLocation("eyeofpunishment", "rig/tiers/" + tier + "_" + state + ".json");
    try (var stream = resources.open(location);
        var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
      var json = new Gson().fromJson(reader, com.google.gson.JsonObject.class);
      String anchor = json.get("anchor").getAsString();
      if (!anchor.equals(state.equals("fire") ? "muzzle_flash" : "receiver"))
        throw new IllegalArgumentException("Invalid original tier effect anchor: " + location);
      var definition = new Gson().fromJson(json, MuzzleGeometry.Definition.class);
      if (definition.emitters == null || definition.emitters.length == 0)
        throw new IllegalArgumentException("Empty original tier effect: " + location);
      return new Effect(definition, anchor);
    }
  }

  private static String tier(ResourceLocation gun) {
    if (gun == null || !gun.getNamespace().equals("eyeofpunishment")) return "";
    return switch (gun.getPath()) {
      case "notdistinguish_base" -> "base";
      case "notdistinguish_tier1" -> "tier1";
      default -> "";
    };
  }

  @SubscribeEvent(priority=EventPriority.LOWEST)
  public static void fired(GunFireEvent event) {
    if (event.isCanceled() || event.getLogicalSide() != LogicalSide.CLIENT) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null) return;
    var stack = event.getGunItemStack();
    var gun = IGun.getIGunOrNull(stack);
    String key = gun == null ? "" : tier(gun.getGunId(stack));
    if (key.isEmpty()) return;
    if (level != mc.level) { SHOTS.clear(); level = mc.level; }
    UUID who = event.getShooter().getUUID();
    double now = level.getGameTime() + mc.getFrameTime();
    Shot previous = SHOTS.get(who);
    double start = previous != null && key.equals(previous.tier)
        && now - previous.last < SHOT_KEEPALIVE_TICKS ? previous.start : now;
    SHOTS.put(who, new Shot(key, start, now));
  }

  private static float firingAge(String key, ItemDisplayContext context) {
    var mc = Minecraft.getInstance();
    var entity = SkillVisualState.entityFor(context);
    if (level == null || level != mc.level || entity == null || !entity.isAlive()) return -1;
    Shot shot = SHOTS.get(entity.getUUID());
    if (shot == null || !key.equals(shot.tier)) return -1;
    if (entity == mc.player && !mc.options.keyAttack.isDown()) return -1;
    double now = level.getGameTime() + mc.getFrameTime();
    return now >= shot.start && now - shot.last < SHOT_KEEPALIVE_TICKS ? (float)(now - shot.start) : -1;
  }

  static void render(String key, PoseStack receiver, PoseStack muzzle,
      ItemDisplayContext context, int overlay) {
    Style style = STYLES.get(key);
    if (style == null) return;
    var mc = Minecraft.getInstance();
    float skillAge = SkillVisualState.activeAgeFor(context);
    float age = skillAge >= 0 ? skillAge : mc.level == null ? 0
        : (float)(mc.level.getGameTime() % 24000) + mc.getFrameTime();
    renderEffect(skillAge >= 0 ? style.active : style.normal, receiver, muzzle, overlay, age);
    float fireAge = firingAge(key, context);
    if (fireAge >= 0) renderEffect(style.fire, receiver, muzzle, overlay, fireAge);
  }

  private static void renderEffect(Effect effect, PoseStack receiver, PoseStack muzzle, int overlay, float age) {
    PoseStack pose = WeaponVisuals.effectPose(effect.anchor, receiver, muzzle);
    if (pose == null) return;
    var definition = effect.data;
    var layers = new TreeSet<Integer>();
    if (definition.passes != null) for (var pass : definition.passes) layers.add(pass.layer);
    if (definition.emitters != null) for (var emitter : definition.emitters) layers.add(emitter.layer);
    for (int layer : layers) {
      if (definition.passes != null) for (var pass : definition.passes) if (pass.layer == layer) {
        MuzzleGeometry.render(pass, pose, BUFFERS.getBuffer(MuzzleRenderTypes.layer(texture(pass.texture), pass.blend)), age, overlay);
        BUFFERS.endBatch();
      }
      if (definition.emitters != null) for (var emitter : definition.emitters) if (emitter.layer == layer) {
        MuzzleParticles.render(emitter, pose, BUFFERS.getBuffer(MuzzleRenderTypes.layer(texture(emitter.texture), emitter.blend)), age, overlay);
        BUFFERS.endBatch();
      }
    }
  }

  private static ResourceLocation texture(String name) {
    return TEXTURES.computeIfAbsent(name, n -> new ResourceLocation("eyeofpunishment", "textures/fx/" + n + ".png"));
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    var mc = Minecraft.getInstance();
    if (level != mc.level) { SHOTS.clear(); level = mc.level; }
    if (level != null) SHOTS.entrySet().removeIf(entry -> level.getGameTime() - entry.getValue().last > 20);
  }
}
