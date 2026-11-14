package cn.ssjjzeus;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.ForgeMod;

/** Predict a straight, fixed-speed shot; never steers an orb after it has been fired. */
public final class ZeusAim {
    public record Shot(Vec3 point,Vec3 direction,double ticks,boolean path) {}
    private record Motion(ServerLevel level,long tick,Vec3 position,Vec3 velocity) {}
    private static final Map<Entity,Motion> HISTORY=new IdentityHashMap<>();
    private static final double EPS=1e-8;
    private ZeusAim() {}
    private static boolean finite(Vec3 value){return value!=null&&Double.isFinite(value.x)&&Double.isFinite(value.y)&&Double.isFinite(value.z);}
    private static double root(Vec3 relative,Vec3 velocity,double speed,double from,double to) {
        double a=velocity.lengthSqr()-speed*speed,b=2*relative.dot(velocity),c=relative.lengthSqr();
        if(c<EPS&&from<=EPS)return 0;
        if(Math.abs(a)<EPS){double t=Math.abs(b)<EPS?Double.NaN:-c/b;return t>=from-EPS&&t<=to+EPS?Math.max(from,t):Double.NaN;}
        double discriminant=b*b-4*a*c;if(discriminant<0)return Double.NaN;
        double square=Math.sqrt(discriminant),q=-.5*(b+Math.copySign(square,b));
        double first=q/a,second=Math.abs(q)<EPS?-b/(2*a):c/q;
        if(first>second){double swap=first;first=second;second=swap;}
        if(first>=from-EPS&&first<=to+EPS)return Math.max(from,first);
        return second>=from-EPS&&second<=to+EPS?Math.max(from,second):Double.NaN;
    }
    private static Shot shot(Vec3 origin,Vec3 point,double ticks,boolean path) {
        Vec3 delta=point.subtract(origin);if(!finite(point)||!Double.isFinite(ticks)||delta.lengthSqr()<EPS)return null;
        return new Shot(point,delta.normalize(),ticks,path);
    }
    public static Shot intercept(Vec3 origin,Vec3 target,Vec3 velocity,double speed,double maxTicks) {
        if(!finite(origin)||!finite(target)||!finite(velocity)||!Double.isFinite(speed)||speed<=0||!Double.isFinite(maxTicks)||maxTicks<=0)return null;
        double t=root(target.subtract(origin),velocity,speed,0,maxTicks);
        return Double.isFinite(t)?shot(origin,target.add(velocity.scale(t)),t,false):null;
    }
    /** Each path segment has constant target speed, then the target stops at its final node. */
    public static Shot interceptPath(Vec3 origin,List<Vec3> points,double targetSpeed,double speed,double maxTicks) {
        if(!finite(origin)||points==null||points.isEmpty()||points.stream().anyMatch(p->!finite(p))
            ||!Double.isFinite(targetSpeed)||targetSpeed<=EPS||!Double.isFinite(speed)||speed<=0||!Double.isFinite(maxTicks)||maxTicks<=0)return null;
        double time=0;Vec3 start=points.get(0);
        for(int i=1;i<points.size()&&time<maxTicks;i++) {
            Vec3 end=points.get(i),delta=end.subtract(start);double length=delta.length();if(length<EPS){start=end;continue;}
            Vec3 velocity=delta.scale(targetSpeed/length);double until=time+length/targetSpeed;
            double t=root(start.subtract(origin).subtract(velocity.scale(time)),velocity,speed,time,Math.min(until,maxTicks));
            if(Double.isFinite(t))return shot(origin,start.add(velocity.scale(t-time)),t,true);
            time=until;start=end;
        }
        double t=start.distanceTo(origin)/speed;
        return t>=time-EPS&&t<=maxTicks?shot(origin,start,t,true):null;
    }
    /** Future sample i is the predicted position i server ticks from now. */
    public static Shot interceptSamples(Vec3 origin,List<Vec3> points,double speed,double maxTicks) {
        if(!finite(origin)||points==null||points.size()<2||points.stream().anyMatch(p->!finite(p))
            ||!Double.isFinite(speed)||speed<=0||!Double.isFinite(maxTicks)||maxTicks<=0)return null;
        for(int i=0;i<points.size()-1&&i<maxTicks;i++) {
            Vec3 velocity=points.get(i+1).subtract(points.get(i));
            double t=root(points.get(i).subtract(origin).subtract(velocity.scale(i)),velocity,speed,i,Math.min(i+1,maxTicks));
            if(Double.isFinite(t))return shot(origin,points.get(i).add(velocity.scale(t-i)),t,true);
        }
        return null;
    }
    static Vec3 velocity(Entity target) {
        if(!(target.level() instanceof ServerLevel level))return Vec3.ZERO;
        long now=level.getGameTime();Vec3 position=target.position(),nativeVelocity=target.getDeltaMovement();
        if(!finite(nativeVelocity))nativeVelocity=Vec3.ZERO;
        Motion previous=HISTORY.get(target);if(previous!=null&&previous.level==level&&previous.tick==now)return previous.velocity;
        Vec3 velocity=nativeVelocity;
        if(previous!=null&&previous.level==level) {
            long elapsed=now-previous.tick;
            if(elapsed>0&&elapsed<=5) {
                Vec3 measured=position.subtract(previous.position).scale(1d/elapsed);
                // A teleport with ordinary/no physical velocity must not become a huge lead.
                // Preserve genuine fast motion so an outrunning target yields no false solution.
                velocity=measured.lengthSqr()>16&&nativeVelocity.lengthSqr()<1?Vec3.ZERO:measured;
            }
        }
        if(!finite(velocity))velocity=Vec3.ZERO;
        HISTORY.put(target,new Motion(level,now,position,velocity));return velocity;
    }
    static Shot aim(ServerLevel level,LivingEntity owner,Vec3 origin,Entity target) {
        Vec3 measured=velocity(target),center=target.getBoundingBox().getCenter();Shot result=null;
        if(!ZeusConfig.enabled("prediction_enabled")) {
            var direct=intercept(origin,center,Vec3.ZERO,ZeusThunder.speed(),ZeusThunder.lifetime());
            return direct!=null&&level.clip(new ClipContext(origin,direct.point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()==HitResult.Type.MISS?direct:null;
        }
        if(target instanceof Mob mob&&target.onGround()&&mob.getNavigation()!=null) {
            var route=mob.getNavigation().getPath();double speed=measured.horizontalDistance();
            if(speed<EPS&&route!=null&&!route.isDone())speed=Math.max(0,mob.getSpeed());
            if(route!=null&&!route.isDone()&&speed>EPS) {
                List<Vec3> points=new ArrayList<>();points.add(center);
                double offset=center.y-target.getY();
                for(int i=route.getNextNodeIndex();i<route.getNodeCount()&&points.size()<65;i++) {
                    var node=route.getEntityPosAtNode(mob,i);points.add(new Vec3(node.x,node.y+offset,node.z));
                }
                result=interceptPath(origin,points,speed,ZeusThunder.speed(),ZeusThunder.lifetime());
            }
        }
        if(result==null&&!target.onGround()&&!target.isNoGravity()&&!target.isInWater()&&target instanceof LivingEntity living) {
            double gravity=living.getAttribute(ForgeMod.ENTITY_GRAVITY.get())==null?.08:living.getAttributeValue(ForgeMod.ENTITY_GRAVITY.get());
            Vec3 delta=target.getDeltaMovement();double vy=finite(delta)?delta.y:measured.y;
            List<Vec3> points=new ArrayList<>();points.add(center);Vec3 position=center;boolean landed=false;
            double feetOffset=center.y-target.getY();
            for(int i=0;i<ZeusThunder.lifetime();i++) {
                Vec3 next=position.add(measured.x,landed?0:vy,measured.z);
                if(!landed&&vy<0) {
                    Vec3 feet=position.add(0,-feetOffset,0),end=next.add(0,-feetOffset,0);
                    var floor=level.clip(new ClipContext(feet,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,target));
                    if(floor.getType()!=HitResult.Type.MISS){next=new Vec3(next.x,floor.getLocation().y+feetOffset,next.z);landed=true;}
                }
                points.add(next);position=next;vy=(vy-gravity)*.98;
            }
            result=interceptSamples(origin,points,ZeusThunder.speed(),ZeusThunder.lifetime());
        }
        if(result==null)result=intercept(origin,center,measured,ZeusThunder.speed(),ZeusThunder.lifetime());
        if(result==null)return null;
        var wall=level.clip(new ClipContext(origin,result.point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
        return wall.getType()==HitResult.Type.MISS?result:null;
    }
    static void prune() {
        HISTORY.entrySet().removeIf(e->e.getKey().isRemoved()||!e.getKey().isAlive()||e.getKey().level()!=e.getValue().level
            ||e.getValue().level.getGameTime()-e.getValue().tick>ZeusThunder.lifetime());
    }
    static void clear(){HISTORY.clear();}
}
