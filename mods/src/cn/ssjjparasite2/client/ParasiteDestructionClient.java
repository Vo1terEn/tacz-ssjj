package cn.ssjjparasite2.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-confirmed body markers. No status effects or client-side damage prediction. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ParasiteDestructionClient {
    private record Key(UUID target,UUID shooter) {}
    private record Mark(ParasiteEffectsNetwork.DestructionMark message,long until,ParasiteEffectsNetwork.DestructionMark resume) {}
    private static final Map<Key,Mark> marks=new LinkedHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    public static final int MAX_FLASH_TICKS=10,MAX_MARKERS=256;
    private ParasiteDestructionClient() {}

    public static int frame(double seconds){return Math.floorMod((int)Math.floor(seconds*12.5),8)+1;}
    public static String label(int count){return count>=5?"MAX":Integer.toString(count);}
    public static ResourceLocation texture(double seconds){return new ResourceLocation("ssjjparasite2","textures/effects/destruction/dannum"+frame(seconds)+".png");}
    public static void receive(ParasiteEffectsNetwork.DestructionMark message){
        var mc=Minecraft.getInstance();if(mc.level==null||!message.valid())return;
        if(mc.level!=lastLevel){lastLevel=mc.level;marks.clear();}
        var key=new Key(message.targetUuid(),message.shooter());
        long now=mc.level.getGameTime();var old=marks.get(key);
        if(old!=null&&old.message.exploded()&&now<old.until&&!message.exploded()){
            // Ordered MAX then 4 preserves the flash and queues the retained stack.
            // A clear cancels any queued stack while a death flash can finish.
            marks.put(key,new Mark(old.message,old.until,message.count()==0?null:message));return;
        }
        if(message.count()==0){marks.remove(key);return;}
        if(!marks.containsKey(key)&&marks.size()>=MAX_MARKERS)marks.remove(marks.keySet().iterator().next());
        marks.put(key,new Mark(message,message.exploded()?now+MAX_FLASH_TICKS:Long.MAX_VALUE,null));
        if(message.exploded())ParasiteEffectsClient.receive(new ParasiteEffectsNetwork.Hit(message.x(),message.y(),message.z(),0,0,0,message.target()^(int)mc.level.getGameTime(),message.profile()));
    }
    @SubscribeEvent public static void tickDestructionMarkers(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level!=lastLevel){lastLevel=mc.level;marks.clear();return;}
        if(mc.level==null)return;
        var iterator=marks.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();var mark=entry.getValue();
            var entity=mc.level.getEntity(mark.message.target());
            // Do not attach a stale marker to a reused network entity ID.
            if(entity==null||!entity.getUUID().equals(mark.message.targetUuid())||(!entity.isAlive()&&!mark.message.exploded())){iterator.remove();continue;}
            if(mc.level.getGameTime()>=mark.until){
                if(mark.resume!=null&&entity.isAlive())entry.setValue(new Mark(mark.resume,Long.MAX_VALUE,null));
                else iterator.remove();
            }
        }
    }
    /** Place the marker on the camera-facing surface of the interpolated body. */
    public static Vec3 surface(AABB body,Vec3 camera){
        Vec3 center=body.getCenter(),direction=camera.subtract(center).normalize();
        double scale=Math.max(Math.abs(direction.x)/Math.max(.01,body.getXsize()*.5),Math.max(Math.abs(direction.y)/Math.max(.01,body.getYsize()*.5),Math.abs(direction.z)/Math.max(.01,body.getZsize()*.5)));
        return center.add(direction.scale((scale<1e-8?0:1/scale)+.04));
    }
    public static EffectGeometry.Batch icon(double seconds){
        var material=new EffectGeometry.Material();material.texture=texture(seconds).toString();material.color=new float[]{1,1,1,1};material.st=new float[]{1,1,0,0};
        var mesh=new EffectGeometry.Mesh();mesh.positions=new float[][]{{-.24f,-.24f,0},{.24f,-.24f,0},{.24f,.24f,0},{-.24f,.24f,0}};
        mesh.uv=new float[][]{{0,1},{1,1},{1,0},{0,0}};mesh.triangles=new int[][]{{0,1,2},{0,2,3}};
        return new EffectGeometry.Batch(material,mesh,new float[]{1,1,1,1},material.st);
    }
    public static void drawMarker(double seconds,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers){
        var type=EffectRenderType.marker(texture(seconds));
        ParasiteEffectsClient.emit(icon(seconds),pose,buffers.getBuffer(type));
        buffers.endBatch(type);
    }
    public static void drawLabel(Font font,int count,PoseStack pose,net.minecraft.client.renderer.MultiBufferSource.BufferSource buffers){
        String text=label(count);
        buffers.endBatch();
        // Flush while depth is disabled: the native SEE_THROUGH shard itself
        // leaves the incoming depth state unchanged.
        RenderSystem.disableDepthTest();
        try {
            font.drawInBatch(text,-font.width(text)*.5f,-font.lineHeight*.5f,count==5?0xfff080:0xffffff,true,pose.last().pose(),buffers,Font.DisplayMode.SEE_THROUGH,0,15728880);
            buffers.endBatch();
        } finally {
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(515);
        }
    }
    @SubscribeEvent public static void renderDestructionMarkers(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||marks.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        double seconds=(mc.level.getGameTime()%240000+event.getPartialTick())/20.;
        Map<UUID,Integer> offsets=new HashMap<>();
        for(var mark:marks.values()){
            var message=mark.message;var entity=mc.level.getEntity(message.target());
            if(!(entity instanceof LivingEntity target)||!target.getUUID().equals(message.targetUuid())||camera.distanceToSqr(target.position())>96*96)continue;
            double x=Mth.lerp(event.getPartialTick(),target.xOld,target.getX()),y=Mth.lerp(event.getPartialTick(),target.yOld,target.getY()),z=Mth.lerp(event.getPartialTick(),target.zOld,target.getZ());
            Vec3 anchor=surface(target.getBoundingBox().move(x-target.getX(),y-target.getY(),z-target.getZ()),camera);
            int index=offsets.merge(target.getUUID(),1,Integer::sum)-1;
            PoseStack pose=event.getPoseStack();pose.pushPose();pose.translate(anchor.x-camera.x,anchor.y-camera.y,anchor.z-camera.z);pose.mulPose(event.getCamera().rotation());pose.translate(index*.55,0,0);
            try {
                drawMarker(seconds,pose,buffers);
                pose.translate(0,0,-.015);pose.scale(-.024f,-.024f,.024f);
                drawLabel(mc.font,message.count(),pose,buffers);
            } finally { pose.popPose(); }
        }
        buffers.endBatch();
    }
}
