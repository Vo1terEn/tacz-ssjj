package cn.judgfire;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Default-skin world visuals only. Callers retain every damage, packet and input path.
 * The supplied pose already contains -camera, so all positions below are world positions.
 */
public final class DefaultWorldFlames {
  private static final Map<JudgmentFlame.CrownStrike,Vec3> CROWN_STARTS=new java.util.IdentityHashMap<>();
  private static final Map<JudgmentFlame.CrownStrike,Integer> CROWN_OWNERS=new java.util.IdentityHashMap<>();
  private static boolean meshAttempted;

  public static void resetWorld(){CROWN_STARTS.clear();CROWN_OWNERS.clear();NativeWorldFlames.resetWorld();SkinWorldFlames.resetWorld();}

  /** Optional resource reload hook; lazy load also supports existing BodyEffects reload ordering. */
  public static void reload(ResourceManager resources) {
    meshAttempted = true;
    CROWN_STARTS.clear();CROWN_OWNERS.clear();
    NativeWorldFlames.reload(resources);
    SkinWorldFlames.reload(resources);
  }

  public static void laser(PoseStack pose, Vec3 start, Vec3 end, Vec3 camera, double age, boolean hit) {
    // The accepted longxidan beam is rendered in gun space. A Shot hit is not a crown strike.
  }

  public static void impact(PoseStack pose, Vec3 end, Vec3 camera, double age) {
    // E014 belongs exclusively to CrownStrike arrival; ordinary bullets/laser have no crown explosion.
  }

  private static Quaternionf cameraRotation() {
    return Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
  }

  public static void mark(PoseStack pose, Vec3 p, double seconds) {
    if (!meshAttempted) reload(Minecraft.getInstance().getResourceManager());
    NativeWorldFlames.mark(pose,p,cameraRotation(),seconds);
  }

  public static void crown(PoseStack pose, Vec3 p, Quaternionf camera, double charge, double seconds) {
    if (!meshAttempted) reload(Minecraft.getInstance().getResourceManager());
    String effect=charge>=67?"E013":charge>=34?"E012":"E011";
    // Default branch supplies actor feet. Native fire, core, glow and ring retain source Y=2.8m.
    NativeWorldFlames.crown(effect,pose,p,camera,seconds);
  }

  public static void strike(PoseStack pose, JudgmentFlame.CrownStrike packet, double age, Quaternionf camera) {
    if (age < 0 || !finite(packet.start()) || !finite(packet.end())) return;
    double duration = CrownHoming.duration(packet.start(), packet.end());
    Vec3 end = packet.end();
    if (!meshAttempted) reload(Minecraft.getInstance().getResourceManager());
    if (age < duration) {
      // The server packet already starts at the shooter's crown. Retain its native homing path.
      // E016 uses only the actual source child nodes and source TrailRenderer; no atlas substitute.
      NativeWorldFlames.projectile(pose,crownStart(packet),end,camera,age,duration);
    } else {
      double impactAge = age - duration;
      if (impactAge < 1.05) {
        if (!meshAttempted) reload(Minecraft.getInstance().getResourceManager());
        // Event100072 mounts at the hit root; preserve all native child offsets without artificial recentering.
        NativeWorldFlames.impact(pose,end,camera,impactAge);
      }
    }
  }

  /** Visual owner only; packet carries no shooter id. Ambiguous candidates stay -1. */
  public static int crownOwner(JudgmentFlame.CrownStrike packet) {
    crownStart(packet);return CROWN_OWNERS.getOrDefault(packet,-1);
  }

  private static Vec3 crownStart(JudgmentFlame.CrownStrike packet) {
    if(CROWN_STARTS.size()>=512){CROWN_STARTS.clear();CROWN_OWNERS.clear();}
    return CROWN_STARTS.computeIfAbsent(packet, p -> {
      CROWN_OWNERS.put(p,-1);
      var mc=Minecraft.getInstance();if(mc.level==null)return p.start();
      net.minecraft.world.entity.player.Player best=null;double closest=Double.POSITIVE_INFINITY,second=Double.POSITIVE_INFINITY;
      for(var entity:mc.level.entitiesForRendering()){
        if(!(entity instanceof net.minecraft.world.entity.player.Player player)||!player.isAlive()
            ||!JudgmentFlame.crown(player.getMainHandItem())||!DefaultFlameEffects.isOriginal(player.getMainHandItem()))continue;
        Vec3 serverMount=player.position().add(0,player.getBbHeight()+.63,0);
        double distance=serverMount.distanceToSqr(p.start());if(distance>.64)continue;
        if(distance<closest){second=closest;closest=distance;best=player;}else second=Math.min(second,distance);
      }
      // The packet has no shooter id. Keep its position whenever two candidates are similarly near.
      if(best==null||Math.sqrt(second)-Math.sqrt(closest)<.2)return p.start();
      CROWN_OWNERS.put(p,best.getId());
      return p.start().add(0,2.8-(best.getBbHeight()+.63),0);
    });
  }

  public static void burst(PoseStack pose, Vec3 base, Quaternionf camera, double age) {
    // Source E010 controller has one real three-plane mesh and two source quad layers.
    // Activation alignment is retained from reference lift-to-peak timing.
    double nativeAge=age-.433;if(nativeAge<0||nativeAge>=1.5)return;
    if (!meshAttempted) reload(Minecraft.getInstance().getResourceManager());
    NativeWorldFlames.draw("E010",pose,base,camera,nativeAge,false);
  }

  private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
}
