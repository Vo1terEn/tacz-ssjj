package cn.ssjjares.client;

import cn.ssjjdark.client.DarkLiberationMotion;
import cn.ssjjdark.client.DarkLiberationPresentation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.IOException;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Named original ANM rotations drive the existing 38-bone weighted Ares renderer. */
public final class DarkAresLiberation {
    private static AresWeightedBodyMesh mesh;
    private static final Map<UUID,AresWeightedBodyMesh.Scratch> PHANTOMS=new LinkedHashMap<>();
    private record Feet(int[] vertices,float floor){}
    private static final Map<AresWeightedBodyMesh,Feet> FEET=new WeakHashMap<>();
    private DarkAresLiberation(){}
    public static void reset(){mesh=null;PHANTOMS.clear();FEET.clear();}
    public static void forget(UUID id){PHANTOMS.remove(id);}
    public static boolean normal(AresSourceBodyVisuals.SourceModel model){
        var player=model.entity;if(player==null)return false;var mc=Minecraft.getInstance();if(mc.level==null)return false;
        if(!DarkLiberationMotion.ready())try{DarkLiberationMotion.read(mc.getResourceManager());}catch(IOException failure){return false;}
        var motion=DarkLiberationPresentation.motion(player.getUUID(),mc.level.getGameTime(),mc.getFrameTime());
        if(motion==null)return false;apply(model.mesh,model.scratch,motion.clock());return true;
    }
    /** Translation remains the target skeleton's own fixed bone length, never a source person's length. */
    public static int apply(AresWeightedBodyMesh mesh,AresWeightedBodyMesh.Scratch scratch,DarkLiberationPresentation.Clock time){
        if(time.recovery()>=1)return 0;
        int used=0;var q=new Quaternionf();var back=new Quaternionf();var position=new Vector3f();float recovery=Math.max(0,Math.min(1,time.recovery()));
        for(int i=0;i<mesh.bones;i++){
            if(DarkLiberationMotion.ares(mesh.names[i],time.sourceSeconds(),q)==null)continue;int k=i*7;
            if(DarkLiberationMotion.aresPosition(mesh.names[i],time.sourceSeconds(),position)!=null){for(int axis=0;axis<3;axis++)scratch.local[k+axis]=position.get(axis)+(scratch.local[k+axis]-position.get(axis))*recovery;}
            back.set(scratch.local[k+3],scratch.local[k+4],scratch.local[k+5],scratch.local[k+6]);q.slerp(back,Math.max(0,Math.min(1,time.recovery()))).normalize();scratch.local[k+3]=q.x;scratch.local[k+4]=q.y;scratch.local[k+5]=q.z;scratch.local[k+6]=q.w;used++;
        }
        // Quaternion interpolation does not preserve leg-chain height. Correct
        // the private/model root during recovery, keeping the actual player free.
        if(recovery>0&&recovery<1){var feet=FEET.computeIfAbsent(mesh,DarkAresLiberation::feet);mesh.skin(scratch);float min=Float.POSITIVE_INFINITY;for(int i:feet.vertices)min=Math.min(min,scratch.position[i*3+1]);if(Float.isFinite(min)){var shift=new Matrix4f(mesh.conversion).invert().transformDirection(0,feet.floor-min,0,new Vector3f());for(int axis=0;axis<3;axis++)scratch.local[mesh.root*7+axis]+=shift.get(axis);}}
        return used;
    }
    private static Feet feet(AresWeightedBodyMesh body){
        var vertices=new ArrayList<Integer>();for(int i=0;i<body.vertices;i++){float weight=0;for(int j=0;j<4;j++){String name=body.names[body.joints[i*4+j]&255];if(name.contains("_Foot")||name.contains("_Toe"))weight+=body.source[i*12+8+j];}if(weight>.2f)vertices.add(i);}var neutral=body.scratch();body.sample("idle",0,neutral.local,neutral);body.skin(neutral);float floor=Float.POSITIVE_INFINITY;for(int i:vertices)floor=Math.min(floor,neutral.position[i*3+1]);return new Feet(vertices.stream().mapToInt(Integer::intValue).toArray(),floor);
    }
    private static AresWeightedBodyMesh mesh()throws IOException {
        if(mesh!=null)return mesh;var manager=Minecraft.getInstance().getResourceManager();try(var body=manager.open(new ResourceLocation("ssjj_ares","models/ares_weighted_body.bin"));var motion=manager.open(new ResourceLocation("ssjj_ares","models/ares_weighted_motion.bin"))){return mesh=AresWeightedBodyMesh.read(body,motion);}
    }
    public static boolean draw(UUID cast,int tier,DarkLiberationPresentation.Clock time,PoseStack pose,MultiBufferSource buffers,int light,AbstractClientPlayer owner,ItemStack weapon){
        try{
            var body=mesh();var scratch=PHANTOMS.computeIfAbsent(cast,k->body.scratch());while(PHANTOMS.size()>128)PHANTOMS.remove(PHANTOMS.keySet().iterator().next());
            body.sample("idle",0,scratch.local,scratch);apply(body,scratch,time);body.skin(scratch);
            body.draw(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(new ResourceLocation("ssjj_ares","textures/entity/ares_weighted_body"+(tier==2?2:1)+".png"))),light,OverlayTexture.NO_OVERLAY,scratch);
            if(owner!=null&&!weapon.isEmpty()){
                pose.pushPose();try{var hand=body.jointPose(scratch,"WeaponAnchor",new Matrix4f());float x=hand.m30(),y=hand.m31(),z=hand.m32();hand.normalize3x3().setTranslation(x,y,z);pose.mulPoseMatrix(hand);pose.last().normal().set(pose.last().pose()).invert().transpose();pose.mulPose(Axis.ZP.rotationDegrees(-90));pose.mulPose(Axis.XP.rotationDegrees(90));cn.ssjjdark.client.DarkLiberationHeldItem.render(owner,weapon,"thirdperson_hand_ares",pose,buffers,light);}finally{pose.popPose();}
            }return true;
        }catch(IOException failure){return false;}
    }
}
