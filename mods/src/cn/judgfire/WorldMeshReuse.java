package cn.judgfire;

import java.util.*;
import org.joml.*;
import net.minecraft.world.phys.Vec3;
import cn.fusionarsenal.config.ArsenalConfig;
import static java.lang.Math.*;

/** Render-only borrowed arrays; public sample calls retain their original snapshot contract. */
public final class WorldMeshReuse {
 private static final float[][] QUAD={{-.5f,-.5f,0},{.5f,-.5f,0},{.5f,.5f,0},{-.5f,.5f,0}},UV={{0,0},{1,0},{1,1},{0,1}};
 private static final int[][] FACES={{0,1,2,3}};
 private static final float[] WHITE={1,1,1,1};
 private static final class Slot {
  final float[][] points,positions,uv,colors;final float[] tint=new float[4],custom=new float[4];
  final SkinWorldFlames.Mesh mesh;
  Slot(SkinWorldFlames.Layer l,int count,int[][] faces){
   points=new float[count][3];positions=new float[faces.length*4][];uv=new float[faces.length*4][2];colors=new float[faces.length*4][];
   int n=0;for(int[] f:faces)for(int j=0;j<4;j++){int i=f[min(j,f.length-1)];positions[n]=points[i];colors[n]=!l.customParticle&&l.vertexColorsUsedByShader&&l.vertexColors!=null?l.vertexColors[i]:WHITE;n++;}
   mesh=new SkinWorldFlames.Mesh(l.material,positions,uv,colors,tint,custom,l.name);
  }
 }
 private static final class Bank {int used,vertices;final ArrayList<Slot> slots=new ArrayList<>();}
 private static final class Pool {int entries,vertices,maxEntries,maxVertices;boolean reuse;final IdentityHashMap<SkinWorldFlames.Layer,Bank> banks=new IdentityHashMap<>();final Vector3f center=new Vector3f(),velocity=new Vector3f(),rr=new Vector3f(),uu=new Vector3f(),p=new Vector3f(),look=new Vector3f();final Quaternionf rotation=new Quaternionf();}
 private static final class Local {int depth;long borrowed,missed;final ArrayList<Pool> pools=new ArrayList<>();}
 private static final ThreadLocal<Local> LOCAL=ThreadLocal.withInitial(Local::new);
 private WorldMeshReuse(){}
 public static boolean enabled(){return ArsenalConfig.bool("render.fire_world");}
 public static boolean active(){Local s=LOCAL.get();return s.depth>0&&s.depth<=8&&s.pools.get(s.depth-1).reuse;}
 public static void begin(){Local s=LOCAL.get();s.depth++;if(s.depth>8)return;while(s.pools.size()<s.depth)s.pools.add(new Pool());Pool p=s.pools.get(s.depth-1);p.reuse=ArsenalConfig.bool("render.reuse_geometry");p.maxEntries=ArsenalConfig.integer("render.world_pool_entries");p.maxVertices=ArsenalConfig.integer("render.world_max_vertices");if(p.entries>p.maxEntries||p.vertices>p.maxVertices){p.banks.clear();p.entries=p.vertices=0;}for(Bank b:p.banks.values())b.used=0;}
 public static void end(){Local s=LOCAL.get();if(s.depth<=0)throw new IllegalStateException("Unbalanced world render scope");s.depth--;}
 public static void clear(){LOCAL.remove();}
 public static long[] statistics(){Local s=LOCAL.get();long entries=0,vertices=0;for(Pool p:s.pools){entries+=p.entries;vertices+=p.vertices;}return new long[]{s.borrowed,s.missed,entries,vertices,s.depth};}
 private static Slot slot(Pool p,SkinWorldFlames.Layer l,int count,int[][] faces){
  int charge=count+faces.length*4;Bank b=p.banks.get(l);
  if(b!=null&&b.used<b.slots.size()){LOCAL.get().borrowed++;return b.slots.get(b.used++);}
  // Cached banks unused by this render may be evicted without mutating any borrowed arrays.
  int maxEntries=p.maxEntries,maxVertices=p.maxVertices;
  if(charge>maxVertices)return null;
  for(var iterator=p.banks.entrySet().iterator();(p.entries>=maxEntries||p.vertices+charge>maxVertices)&&iterator.hasNext();){Bank old=iterator.next().getValue();if(old!=b&&old.used==0){p.entries-=old.slots.size();p.vertices-=old.vertices;iterator.remove();}}
  if(p.entries>=maxEntries||p.vertices+charge>maxVertices)return null;
  if(b==null){b=new Bank();p.banks.put(l,b);}
  Slot slot=new Slot(l,count,faces);b.slots.add(slot);b.used++;b.vertices+=charge;p.entries++;p.vertices+=charge;LOCAL.get().missed++;return slot;
 }
 /** Returns false only when the bounded render pool must use the original snapshot fallback. */
 public static boolean add(List<SkinWorldFlames.Mesh> out,SkinWorldFlames.Layer l,float[] c,float[] vel,float[] size,float[] euler,float[] color,float[] st,float[] custom,double time,double birth,Vector3f right,Vector3f up,SkinWorldFlames.Flight flight,Matrix4f world,float[] axisScale){
  if(color[3]<=0||size[0]==0||size[1]==0)return true;
  if(!active())return false;
  float[][] vertices=l.nativeLocalVertices==null?QUAD:l.nativeLocalVertices,uv=l.nativeLocalVertices==null?UV:l.uv;int[][] faces=l.faces==null?FACES:l.faces;
  Pool pool=LOCAL.get().pools.get(LOCAL.get().depth-1);Slot slot=slot(pool,l,vertices.length,faces);if(slot==null)return false;
  boolean billboard=l.customParticle?(l.billboardShader>0||l.billboardType!=4):l.nativeLocalVertices==null&&l.renderAlignment!=2;
  Vector3f center=world.transformPosition(pool.center.set(c[0],c[1],c[2]));center.z=-center.z;
  Vector3f velocity=world.transformDirection(pool.velocity.set(vel[0],vel[1],vel[2]));velocity.z=-velocity.z;
  Quaternionf place=flight==null?null:flight.rotation((l.customParticle?l.sourceIsWorldParticle==1:l.simulationSpace==1)?birth:time);
  if(place!=null){center.rotate(place);velocity.rotate(place);}
  Vec3 delta=null;if(flight!=null&&(l.customParticle?l.sourceIsWorldParticle==1:l.simulationSpace==1)){delta=flight.point(birth).subtract(flight.point(time));center.add((float)delta.x,(float)delta.y,(float)delta.z);}
  Vector3f rr=pool.rr.set(right),uu=pool.uu.set(up);
  if(!l.customParticle&&l.renderMode==2){rr.set(1,0,0);uu.set(0,0,-1);}
  else if(!l.customParticle&&l.renderMode==3){rr.y=0;if(rr.lengthSquared()>1e-10)rr.normalize();uu.set(0,1,0);}
  if((l.customParticle&&l.billboardType==1||!l.customParticle&&l.renderMode==1)&&velocity.lengthSquared()>1e-10){uu.set(velocity).normalize();Vector3f look=pool.look.set(right).cross(up);rr.set(uu).cross(look);if(rr.lengthSquared()<1e-10)rr.set(right);else rr.normalize();}
  Quaternionf rotation=pool.rotation.identity().rotateY(euler[1]).rotateX(euler[0]).rotateZ(euler[2]);
  for(int i=0;i<vertices.length;i++){
   Vector3f p=pool.p.set(vertices[i]).mul(size[0],size[1],size[2]).rotate(rotation);
   if(billboard){float x=p.x,height=p.y;if(!l.customParticle&&l.renderMode==1)height*=max(1,l.lengthScale);p.set(center).fma(x*axisScale[0],rr).fma(height*axisScale[1],uu);}
   else{p.add(c[0],c[1],c[2]);world.transformPosition(p);p.z=-p.z;if(place!=null)p.rotate(place);if(delta!=null)p.add((float)delta.x,(float)delta.y,(float)delta.z);}
   slot.points[i][0]=p.x;slot.points[i][1]=p.y;slot.points[i][2]=p.z;
  }
  int n=0;for(int[] face:faces)for(int j=0;j<4;j++){int i=face[min(j,face.length-1)];slot.uv[n][0]=uv[i][0]*st[0]+st[2];slot.uv[n][1]=uv[i][1]*st[1]+st[3];n++;}
  System.arraycopy(color,0,slot.tint,0,4);System.arraycopy(custom,0,slot.custom,0,4);out.add(slot.mesh);return true;
 }
 public static double retention(SkinWorldFlames.Data data,int skin){
  if(data==null)return 7;SkinWorldFlames.Effect effect=data.effects.get(new String[]{"","qinglong","frozen","lightning"}[skin]+":crown_projectile_impact");if(effect==null)return 0;double end=0;
  for(SkinWorldFlames.Layer l:effect.layers){if(!l.enabled||(!l.staticMesh&&l.max<=0))continue;double life=l.lifeRange==null?l.life:max(l.lifeRange[0],l.lifeRange[1]);if(l.customParticle){var s=l.spec.customSchedule;double born=s==null?0:s.playTime;if(born<=0&&l.spec.sourceStartTimeSamplesRange!=null)for(float[] row:l.spec.sourceStartTimeSamplesRange)for(float x:row)born=max(born,x);end=max(end,born+life);}else{if(l.rate>0)end=max(end,l.delay+l.duration+life);if(l.bursts!=null)for(SkinWorldFlames.Burst b:l.bursts)end=max(end,l.delay+b.time+max(0,b.cycles-1)*b.repeat+life);}}
  return end+.01;
 }
}
