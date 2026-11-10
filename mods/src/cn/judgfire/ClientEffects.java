package cn.judgfire;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Native muzzle snapshots feeding the complete original beam/impact/mark prefabs. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID, value = Dist.CLIENT)
public final class ClientEffects {
  private record Visible(JudgmentFlame.Shot shot, Vec3 start, long born, Object level) {}

  private record Pending(Vec3 start, long born, Object level) {}
  private record Flashed(Vec3 start,Vec3 forward,int skin,long born,Object level) {}
  private static final List<Flashed> FLASHES=new ArrayList<>();

  private record Marked(int target, long expires, long born, Object level, int skin) {}

  private static final List<Visible> SHOTS = new ArrayList<>();
  private static final ArrayDeque<Pending> ORIGINS = new ArrayDeque<>();
  private static final Map<Integer, Marked> MARKS = new HashMap<>();
  private static final Map<ItemStack, Pending> MODEL_MUZZLES = new WeakHashMap<>();
  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void fired(GunFireEvent event) {
    var mc = Minecraft.getInstance();
    if (!event.getLogicalSide().isClient()
        || event.getShooter() != mc.player
        || mc.level == null
        || !JudgmentFlame.ours(event.getGunItemStack())) return;
    long now = System.nanoTime();
    prune(mc.level,now,mc.level.getGameTime());
    prunePending(now, mc.level);
    Vec3 origin = localMuzzle();
    if (!mc.options.getCameraType().isFirstPerson()) {
      Vec3 forward = event.getShooter().getViewVector(1).normalize();
      origin =
          event
              .getShooter()
              .getEyePosition()
              .add(forward.scale(1))
              .add(forward.cross(new Vec3(0, 1, 0)).normalize().scale(.22))
              .add(0, -.25, 0);
      var cached = MODEL_MUZZLES.get(event.getGunItemStack());
      if (cached != null && cached.level == mc.level && now - cached.born < 250_000_000L)
        origin = cached.start;
    }
    FLASHES.add(new Flashed(origin,event.getShooter().getViewVector(1),WeaponSkins.skin(event.getGunItemStack()),now,mc.level));
    if(FLASHES.size()>256)FLASHES.remove(0);
    ORIGINS.addLast(new Pending(origin, now, mc.level));
    while (ORIGINS.size() > 32) ORIGINS.removeFirst();
  }

  private static Vec3 localMuzzle() {
    var mc = Minecraft.getInstance();
    var camera = mc.gameRenderer.getMainCamera();
    // TaCZ already compensates this offset for the distinct hand/world FOVs,
    // including native scope zoom. Copy and rotate ONCE at the fire event.
    return BeamOrigin.snapshot(
        camera.getPosition(), GunItemRendererWrapper.muzzleRenderOffset, camera.rotation());
  }

  private static void prunePending(long now, Object level) {
    ORIGINS.removeIf(p -> p.level != level || now - p.born > 3_000_000_000L);
  }

  public static void capture(
      BedrockGunModel model, PoseStack rootPose, ItemDisplayContext context) {
    if (context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return;
    var mc = Minecraft.getInstance();
    if (mc.level == null || model.getMuzzleFlashPosPath() == null) return;
    // The callback is at root. Remove root once before applying TaCZ's complete
    // muzzle path, then transform camera coordinates back to immutable world coordinates.
    var root = new PoseStack();
    model.getRootNode().translateAndRotateAndScale(root);
    var muzzle = new PoseStack();
    muzzle.last().pose().set(rootPose.last().pose()).mul(new Matrix4f(root.last().pose()).invert());
    for (var part : model.getMuzzleFlashPosPath()) part.translateAndRotateAndScale(muzzle);
    Vector3f v =
        new Vector3f(
            muzzle.last().pose().m30(), muzzle.last().pose().m31(), muzzle.last().pose().m32());
    var camera = mc.gameRenderer.getMainCamera();
    if (Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z))
      MODEL_MUZZLES.put(
          model.getCurrentGunItem(),
          new Pending(
              BeamOrigin.snapshot(camera.getPosition(), v, camera.rotation()),
              System.nanoTime(),
              mc.level));
  }

  public static void accept(JudgmentFlame.Shot shot) {
    var mc = Minecraft.getInstance();
    if (mc.level != null) {
      long now = System.nanoTime();
      prune(mc.level,now,mc.level.getGameTime());
      prunePending(now, mc.level);
      Vec3 start = shot.start();
      boolean firing=shot.laser()||!shot.hit()||shot.start().distanceToSqr(shot.end())>1e-8;
      if (mc.player != null && shot.shooter() == mc.player.getId() && firing) {
        var pending = ORIGINS.pollFirst();
        if (pending != null) start = pending.start;
        else if (mc.options.getCameraType().isFirstPerson()) start = localMuzzle();
      } else {
        var entity = mc.level.getEntity(shot.shooter());
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
          var cached = MODEL_MUZZLES.get(living.getMainHandItem());
          if (cached != null && cached.level == mc.level && now - cached.born < 250_000_000L)
            start = cached.start;
        }
      }
      if(shot.laser()||JudgmentWorldFx.shotImpact(shot.skin(),shot.hit()))SHOTS.add(new Visible(shot,start,now,mc.level));
      if(firing&&(mc.player==null||shot.shooter()!=mc.player.getId())) {
        var shooter=mc.level.getEntity(shot.shooter());
        Vec3 forward=shooter==null?shot.end().subtract(start):shooter.getViewVector(1);
        FLASHES.add(new Flashed(start,forward,shot.skin(),now,mc.level));
        if(FLASHES.size()>256)FLASHES.remove(0);
      }
    }
    if (SHOTS.size() > 256) SHOTS.remove(0);
  }

  public static void mark(JudgmentFlame.Mark packet) {
    var mc = Minecraft.getInstance();
    if (mc.level != null)
      MARKS.put(
          packet.target(),
          new Marked(
              packet.target(),
              mc.level.getGameTime() + packet.duration(),
              mc.level.getGameTime(),
              mc.level,
              packet.skin()));
  }

  // World rendering stops on disconnect. Client ticks still run in menus and with rendering off.
  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    var level = Minecraft.getInstance().level;
    long now=System.nanoTime();
    prune(level,now,level==null?0:level.getGameTime());
    JudgmentWorldFx.prune(level,now);
  }

  static void prune(Object level, long now, long gameTime) {
    if (level == null) {
      SHOTS.clear();
      FLASHES.clear();
      ORIGINS.clear();
      MARKS.clear();
      MODEL_MUZZLES.clear();
      return;
    }
    FLASHES.removeIf(v->v.level!=level||(now-v.born)/1e9>=JudgmentWorldFx.muzzleLifetime(v.skin));
    SHOTS.removeIf(v -> v.level != level || (now - v.born) / 1e9 >= JudgmentWorldFx.shotLifetime(v.shot.skin(), v.shot.laser(), v.shot.hit()));
    MARKS.values().removeIf(v -> v.level != level || gameTime >= v.expires);
    prunePending(now, level);
    MODEL_MUZZLES.values().removeIf(v -> v.level != level || now - v.born >= 250_000_000L);
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
    var mc=Minecraft.getInstance();long now=System.nanoTime();
    prune(mc.level,now,mc.level==null?0:mc.level.getGameTime());
    if(mc.level==null||SHOTS.isEmpty()&&MARKS.isEmpty()&&FLASHES.isEmpty())return;
    var pose=cn.fusionarsenal.firefx.FireFxLibrary.copy(event.getPoseStack());
    Vec3 eye=event.getCamera().getPosition();pose.translate(-eye.x,-eye.y,-eye.z);
    var camera=JudgmentWorldFx.view(event.getPoseStack(),event.getCamera().rotation());
    for(var flash:FLASHES)JudgmentWorldFx.draw(flash.skin,"muzzle",JudgmentWorldFx.at(pose,flash.start,flash.forward),(now-flash.born)/1e9f,camera,flash.born);
    for(var visible:SHOTS) {
      float age=(now-visible.born)/1e9f;var shot=visible.shot;
      if(JudgmentWorldFx.shotImpact(shot.skin(),shot.hit()))JudgmentWorldFx.draw(shot.skin(),"impact",JudgmentWorldFx.at(pose,shot.end(),null),age,camera,visible.born);
      if(!shot.laser())continue;
      Vec3 delta=shot.end().subtract(visible.start);if(delta.lengthSqr()<1e-8)continue;
      JudgmentWorldFx.draw(shot.skin(),"beam",JudgmentWorldFx.at(pose,visible.start,delta),age,camera,visible.born,(float)delta.length(),null);
    }
    for(var marked:MARKS.values()) {
      var target=mc.level.getEntity(marked.target);if(target==null||!target.isAlive())continue;
      Vec3 point=target.getPosition(event.getPartialTick()).add(0,target.getBbHeight()+.32,0);
      double age=(mc.level.getGameTime()+event.getPartialTick()-marked.born)/20.0;
      JudgmentWorldFx.draw(marked.skin,"mark",JudgmentWorldFx.at(pose,point,null),JudgmentWorldFx.cycle(marked.skin,"mark",age),camera,marked.target);
    }
  }
}
