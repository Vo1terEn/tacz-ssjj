package cn.judgfire;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
/** Original assets only refract in visible crown heads of skins0/1/2. */
public final class WorldScenePlan {
 private static long seen=Long.MIN_VALUE;private static boolean wanted;
 private WorldScenePlan(){}
 static boolean needsHead(boolean alive,boolean crown,boolean localFirstPerson,int skin){return alive&&crown&&!localFirstPerson&&skin>=0&&skin<=2;}
 public static void begin(){long frame=WorldFlameRenderType.frame();if(frame!=seen){seen=frame;wanted=false;var mc=Minecraft.getInstance();if(mc.level!=null&&WorldMeshReuse.enabled())for(var e:mc.level.entitiesForRendering()){if(!(e instanceof LivingEntity living)||!living.isAlive()||!JudgmentFlame.crown(living.getMainHandItem()))continue;if(e==mc.player&&mc.options.getCameraType().isFirstPerson())continue;if(needsHead(true,true,false,WeaponSkins.skin(living.getMainHandItem()))){wanted=true;break;}}}if(wanted)WorldFlameRenderType.copyScene();}
 public static void clear(){seen=Long.MIN_VALUE;wanted=false;}
}
