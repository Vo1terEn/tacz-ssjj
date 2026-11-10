package cn.judgfire;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.IdentityHashMap;
import java.util.Map;

/** Matches a world hit's screen position to the independently projected gun.
 * Minecraft/TaCZ use separate world and hand FOVs. Camera/pose inversion alone
 * therefore cannot place an off-centre hit correctly in the hand pass. */
public final class EndpointProjection {
  private EndpointProjection() {}
  private static final long MAX_FRAME_AGE=2_000_000_000L;
  private record Context(boolean firstPerson,long frame) {}
  private static final Map<Matrix4f,Context> CONTEXTS=new IdentityHashMap<>();
  private static Matrix4f worldProjection;
  private static long worldFrame,worldCaptured;

  /** Matrix identities are the actual captured draw contexts, never item IDs. */
  public static void captureContext(Matrix4f pose,boolean firstPerson) {
    if(pose==null)return;
    if(CONTEXTS.size()>=128)CONTEXTS.clear();
    CONTEXTS.put(pose,new Context(firstPerson,worldFrame));
  }
  public static void finishContext(Matrix4f pose){CONTEXTS.remove(pose);}
  /** Keep the just-captured world frame when a held weapon changes this frame. */
  public static void clear(){CONTEXTS.clear();}

  /** target is camera-relative view space after the renderer's X/Z conversion,
   * before inverse(capturedPose). Third-person uses the world projection already. */
  public static void adjustViewEndpoint(Matrix4f pose,Vector3f target) {
    Context context=CONTEXTS.get(pose);
    long age=System.nanoTime()-worldCaptured;
    if(context==null||!context.firstPerson||context.frame!=worldFrame||worldProjection==null
        ||age<0||age>MAX_FRAME_AGE)return;
    compensate(target,worldProjection,RenderSystem.getProjectionMatrix(),true);
  }

  /** Pure production math, shared with offline checks. Homogeneous projection
   * also handles zoom offsets and projection-space camera effects. Both matrices
   * are copied/guarded; failed or singular inputs leave the endpoint untouched. */
  public static Vector3f compensate(Vector3f target,Matrix4f world,Matrix4f weapon,boolean firstPerson) {
    if(!firstPerson||target==null||!target.isFinite()||!RenderPoseGuard.finite(world))return target;
    Matrix4f inverse=RenderPoseGuard.inverse(weapon);if(inverse==null)return target;
    Vector4f adjusted=inverse.mul(world).transform(new Vector4f(target,1));
    if(!adjusted.isFinite()||Math.abs(adjusted.w)<1e-7f)return target;
    Vector3f result=new Vector3f(adjusted.x/adjusted.w,adjusted.y/adjusted.w,adjusted.z/adjusted.w);
    if(result.isFinite())target.set(result);
    return target;
  }

  @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
  public static final class WorldFrame {
    private WorldFrame() {}
    /** In patched GameRenderer this stage receives the exact world projection
     * immediately before renderItemInHand installs its independent projection. */
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void captureWorldProjection(RenderLevelStageEvent event) {
      if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
      Matrix4f projection=event.getProjectionMatrix();
      worldProjection=RenderPoseGuard.finite(projection)?new Matrix4f(projection):null;
      worldCaptured=System.nanoTime();worldFrame++;CONTEXTS.clear();
    }
  }
}
