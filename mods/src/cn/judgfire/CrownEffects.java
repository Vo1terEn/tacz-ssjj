package cn.judgfire;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/** Native charge HUD and complete original crown/projectile/impact prefabs. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID, value = Dist.CLIENT)
public final class CrownEffects {
  private static final ResourceLocation FILL = tex("t178_power_progress"),
      BG = tex("t179_power_progress_bg"),
      FIRE = tex("crown_original_fire"),
      RING = tex("crown_original_ring"),
      BULLET = tex("crown_seeker_t163"),
      TRAIL = tex("crown_trail_t281");
  private record Strike(JudgmentFlame.CrownStrike packet, long born, Object level, int side) {}

  private static final List<Strike> STRIKES = new ArrayList<>();
  private static JudgmentFlame.CrownState state;
  private static Object stateLevel;
  private static int nextSide=1;

  private static ResourceLocation tex(String name) {
    return new ResourceLocation("judgfire", "textures/fx/" + name + ".png");
  }

  public static void state(JudgmentFlame.CrownState packet) {
    state = packet;
    stateLevel = Minecraft.getInstance().level;
  }

  public static void strike(JudgmentFlame.CrownStrike packet) {
    var mc = Minecraft.getInstance();
    if (mc.level != null) {
      long now=System.nanoTime();prune(mc.level,now);
      STRIKES.add(new Strike(packet,now,mc.level,nextSide));nextSide=-nextSide;
      if (STRIKES.size() > 128) STRIKES.remove(0);
    }
  }

  @SubscribeEvent
  public static void tick(TickEvent.ClientTickEvent event) {
    if (event.phase == TickEvent.Phase.END) prune(Minecraft.getInstance().level, System.nanoTime());
  }

  static void prune(Object level, long now) {
    if (stateLevel != level || level == null) {
      state = null;
      stateLevel = null;
      if(level==null)nextSide=1;
    }
    STRIKES.removeIf(
        s ->
            level == null
                || s.level != level
                || (now - s.born) / 1e9
                    >= JudgmentWorldFx.strikeLifetime(s.packet));
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void hud(RenderGuiEvent.Post event) {
    var mc = Minecraft.getInstance();
    if (mc.player == null
        || mc.options.hideGui
        || !JudgmentFlame.crown(mc.player.getMainHandItem())) return;
    var stack = mc.player.getMainHandItem();
    var tag = stack.getTag();
    double charge = tag == null ? 0 : CrownCharge.clamp(tag.getDouble(EmperorCrown.CHARGE));
    if (stateLevel == mc.level
        && state != null
        && tag != null
        && tag.hasUUID(EmperorCrown.ID)
        && state.weapon().equals(tag.getUUID(EmperorCrown.ID))) charge = state.charge();
    var g = event.getGuiGraphics();
    int cx = event.getWindow().getGuiScaledWidth() / 2,
        cy = event.getWindow().getGuiScaledHeight() / 2;
    // Original image pixels at half size: centered above crosshair, not hotbar.
    g.pose().pushPose();
    g.pose().translate(cx - 58.5, cy - 38, 450);
    g.pose().scale(.5f, .5f, 1);
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.setShaderColor(1, 1, 1, 1);
    g.blit(SkinHud.crownBackground(WeaponSkins.skin(stack)), 0, 0, 0, 0, 234, 33, 234, 33);
    int fill = (int) Math.round(215 * CrownCharge.clamp(charge) / 100);
    if (fill > 0) g.blit(SkinHud.crownFill(WeaponSkins.skin(stack)), 10, 12, 0, 0, fill, 9, 215, 9);
    g.pose().popPose();
  }

  @SubscribeEvent
  public static void render(RenderLevelStageEvent event) {
    if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
    var mc=Minecraft.getInstance();long now=System.nanoTime();prune(mc.level,now);if(mc.level==null)return;
    var pose=cn.fusionarsenal.firefx.FireFxLibrary.copy(event.getPoseStack());
    var eye=event.getCamera().getPosition();pose.translate(-eye.x,-eye.y,-eye.z);var camera=JudgmentWorldFx.view(event.getPoseStack(),event.getCamera().rotation());
    for(var entity:mc.level.entitiesForRendering()) {
      if(!(entity instanceof LivingEntity living)||!living.isAlive()||!JudgmentFlame.crown(living.getMainHandItem()))continue;
      if(entity==mc.player&&mc.options.getCameraType().isFirstPerson())continue;
      var stack=living.getMainHandItem();var tag=stack.getTag();double charge=tag==null?0:CrownCharge.clamp(tag.getDouble(EmperorCrown.CHARGE));
      if(stateLevel==mc.level&&state!=null&&tag!=null&&tag.hasUUID(EmperorCrown.ID)&&state.weapon().equals(tag.getUUID(EmperorCrown.ID)))charge=state.charge();
      int skin=WeaponSkins.skin(stack);String stage=JudgmentWorldFx.crownStage(charge);
      var point=entity.getPosition(event.getPartialTick()).add(0,entity.getBbHeight()+.55,0);
      float age=JudgmentWorldFx.cycle(skin,stage,(entity.tickCount+event.getPartialTick())/20.0);
      JudgmentWorldFx.draw(skin,stage,JudgmentWorldFx.at(pose,point,null),age,camera,entity.getId());
    }
    for(var strike:STRIKES) {
      var packet=strike.packet;float age=(now-strike.born)/1e9f;
      double duration=CrownHoming.duration(packet.start(),packet.end());
      if(age<duration+JudgmentWorldFx.projectileTail(packet.skin())) {
        // Native world-space trail particles sample the actual homing curve at their birth time.
        cn.fusionarsenal.firefx.FireFxLibrary.PoseProvider history=(path,bone,sampledAge)->projectilePose(pose,packet,Math.min(duration,Math.max(0,sampledAge)),duration,strike.side);
        JudgmentWorldFx.draw(packet.skin(),"projectile",projectilePose(pose,packet,Math.min(age,duration),duration,strike.side),age,camera,strike.born,Float.NaN,history,packet.skin()==0?(float)duration:Float.POSITIVE_INFINITY);
      }
      if(age>=duration) {
        JudgmentWorldFx.draw(packet.skin(),"impact",JudgmentWorldFx.at(pose,packet.end(),null),(float)(age-duration),camera,strike.born);
      }
    }
  }
  public static final double BASE_VISUAL_ARC_SCALE=3.1;
  /** The first-person crown reference alternates wide left/right arrivals.
   * Adapt only the cosmetic side arc; server endpoints and timing stay native. */
  public static Vec3 projectilePoint(JudgmentFlame.CrownStrike packet,double fraction,int side) {
    double t=Math.max(0,Math.min(1,fraction));var point=CrownHoming.point(packet.start(),packet.end(),t);
    if(packet.skin()!=0)return point;
    var delta=packet.end().subtract(packet.start());var lateral=delta.cross(new Vec3(0,1,0)).normalize();
    double offset=.65*Math.min(1,delta.length()/3)*3*(1-t)*(1-t)*t;
    return point.add(lateral.scale((BASE_VISUAL_ARC_SCALE*(side>=0?1:-1)-1)*offset));
  }
  private static PoseStack projectilePose(PoseStack pose,JudgmentFlame.CrownStrike packet,double age,double duration,int side) {
    double time=Math.max(0,Math.min(1,age/duration));
    var point=projectilePoint(packet,time,side);
    double before=Math.max(0,time-.002),after=Math.min(1,time+.002);
    var forward=projectilePoint(packet,after,side).subtract(projectilePoint(packet,before,side));
    return JudgmentWorldFx.at(pose,point,forward);
  }
}
