package cn.punishmenteye;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID,value=Dist.CLIENT)
public final class GazeVisuals {
    private record Visible(int id,UUID uuid,long expires){}
    private static final Map<UUID,Visible> MARKS=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static final ResourceLocation EYE=new ResourceLocation("eyeofpunishment","textures/fx/god_gaze_eye.png");
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(256));
    public static void accept(GazeNetwork.Mark packet){
        var current=Minecraft.getInstance().level;
        if(current==null||!current.dimension().location().equals(packet.dimension()))return;
        if(level!=current){MARKS.clear();level=current;}
        if(packet.remaining()<=0){MARKS.remove(packet.uuid());return;}
        if(packet.remaining()>72000)return;
        MARKS.put(packet.uuid(),new Visible(packet.id(),packet.uuid(),current.getGameTime()+packet.remaining()));
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var current=Minecraft.getInstance().level;
        if(current!=level){MARKS.clear();level=current;}
        if(current==null)return;
        MARKS.values().removeIf(v->v.expires<=current.getGameTime()
            ||(current.getEntity(v.id)!=null&&(!current.getEntity(v.id).isAlive()||!current.getEntity(v.id).getUUID().equals(v.uuid))));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        var mc=Minecraft.getInstance();
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||mc.level==null||mc.level!=level||MARKS.isEmpty())return;
        var camera=e.getCamera().getPosition();var pose=e.getPoseStack();var type=RenderType.entityTranslucentEmissive(EYE);
        for(var v:MARKS.values()){
            if(v.expires<=mc.level.getGameTime()||!(mc.level.getEntity(v.id) instanceof LivingEntity target)||!target.getUUID().equals(v.uuid)||!target.isAlive()||target.isInvisible())continue;
            if(target==mc.getCameraEntity()&&mc.options.getCameraType().isFirstPerson())continue;
            var p=target.getPosition(e.getPartialTick());
            int frame=(int)(mc.level.getGameTime()/2%6);
            GazeGeometry.render(pose,BUFFERS.getBuffer(type),p.x-camera.x,p.y-camera.y,p.z-camera.z,target.getBbHeight(),mc.getEntityRenderDispatcher().cameraOrientation(),frame);
        }
        BUFFERS.endBatch();
    }
}
