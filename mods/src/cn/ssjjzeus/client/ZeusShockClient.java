package cn.ssjjzeus.client;

import cn.ssjjzeus.ZeusAdventNetwork;
import cn.ssjjdimension.client.DimensionParticles;
import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** The supplied playerStatus 30017 / hand_of_zeuss_dian / gandian body effect. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ZeusShockClient {
    private static final ResourceLocation EFFECT=new ResourceLocation("ssjj_zeus","effects/thunder/hand_of_zeuss2_shock.json");
    private static volatile DimensionParticles.Data source;
    private static boolean fallbackAttempted;
    private record Visible(int entityId,UUID entityUUID,long born,long expires) {}
    private static final Map<UUID,Visible> ACTIVE=new LinkedHashMap<>();
    private static ClientLevel level;
    private ZeusShockClient() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerZeusShockResources(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ZeusShockClient::read);
        }
    }
    public static void read(ResourceManager resources) {
        source=null;fallbackAttempted=false;
        try(var input=resources.open(EFFECT);var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            var data=new Gson().fromJson(reader,DimensionParticles.Data.class);
            DimensionParticles.validate(data,"ssjj_zeus:hand_of_zeuss2_shock","world","ssjj_zeus:textures/effects/");
            if(data.emitters.length!=3)throw new IllegalArgumentException("Missing original gandian layers");
            for(var emitter:data.emitters)if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IllegalArgumentException("Missing gandian texture");
            source=data;
            LogUtils.getLogger().info("ZEUS_SHOCK loaded {} original gandian layers",data.emitters.length);
        } catch(Exception error){LogUtils.getLogger().error("Cannot load original Zeus shock effect",error);}
    }
    private static boolean ready() {
        if(source==null&&!fallbackAttempted) {
            read(Minecraft.getInstance().getResourceManager());
            fallbackAttempted=true;
        }
        return source!=null;
    }
    private static void world() {
        var current=Minecraft.getInstance().level;
        if(current!=level){ACTIVE.clear();level=current;}
    }
    public static boolean current(long now,long expires){return now<expires;}
    public static float bodyWidth(float width){return Math.max(.05f,Math.min(32,width))/.6f;}
    public static float bodyHeight(float height){return Math.max(.05f,Math.min(64,height))/1.8f;}
    public static void accept(ZeusAdventNetwork.Shock packet) {
        world();
        if(packet==null||!packet.valid()||level==null||!level.dimension().location().equals(packet.dimension()))return;
        if(packet.remove()){ACTIVE.remove(packet.targetUUID());return;}
        long now=level.getGameTime();var old=ACTIVE.get(packet.targetUUID());
        long born=old!=null&&old.entityId==packet.targetId()&&current(now,old.expires)?old.born:now;
        ACTIVE.put(packet.targetUUID(),new Visible(packet.targetId(),packet.targetUUID(),born,now+packet.remainingTicks()));
        while(ACTIVE.size()>512)ACTIVE.remove(ACTIVE.keySet().iterator().next());
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;world();if(level==null)return;
        long now=level.getGameTime();
        ACTIVE.values().removeIf(v->{
            if(!current(now,v.expires))return true;
            var entity=level.getEntity(v.entityId);
            // A tracking update may arrive before the native spawn packet.
            return entity==null?now-v.born>10:!entity.getUUID().equals(v.entityUUID)||entity.isRemoved()||!entity.isAlive();
        });
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;world();
        if(level==null||ACTIVE.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.player==null||!ready())return;
        var camera=event.getCamera().getPosition();var rotation=event.getCamera().rotation();
        var right=rotation.transform(new Vector3f(1,0,0));var up=rotation.transform(new Vector3f(0,1,0));
        var pose=event.getPoseStack();long now=level.getGameTime();float partial=event.getPartialTick();
        for(var v:ACTIVE.values()) {
            if(!current(now,v.expires))continue;
            var target=level.getEntity(v.entityId);
            if(target==null||!target.getUUID().equals(v.entityUUID)||target.isRemoved()||!target.isAlive()||target.isSpectator()
                ||target.distanceToSqr(camera.x,camera.y,camera.z)>96*96||!event.getFrustum().isVisible(target.getBoundingBox().inflate(.6)))continue;
            double x=Mth.lerp(partial,target.xOld,target.getX()),y=Mth.lerp(partial,target.yOld,target.getY()),z=Mth.lerp(partial,target.zOld,target.getZ());
            pose.pushPose();pose.translate(x-camera.x,y-camera.y,z-camera.z);
            float width=bodyWidth(target.getBbWidth());pose.scale(width,bodyHeight(target.getBbHeight()),width);
            ZeusEntityEffectRenderer.draw(source,(now-v.born+partial)/20f,right,up,pose);
            pose.popPose();
        }
        ZeusEntityEffectRenderer.flush();
    }
}
