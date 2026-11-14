package cn.ssjjzeus.client;

import cn.ssjjzeus.ZeusAdventNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import cn.ssjjdimension.client.DimensionParticles;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import com.mojang.logging.LogUtils;
import org.joml.Vector3f;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Owner-only server selection using the supplied hand_of_zeuss_goal / jingti effect. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ZeusFocusClient {
    private static final ResourceLocation EFFECT=new ResourceLocation("ssjj_zeus","effects/thunder/hand_of_zeuss2_focus.json");
    private static volatile DimensionParticles.Data source;
    private static boolean fallbackAttempted;
    private static ClientLevel level;
    private static ZeusAdventNetwork.Focus selected;
    private static long markedAt;
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Resources {
        @SubscribeEvent public static void registerZeusFocusResources(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener)ZeusFocusClient::read);
        }
    }
    public static void read(ResourceManager resources) {
        source=null;fallbackAttempted=false;
        try(var input=resources.open(EFFECT);var reader=new InputStreamReader(input,StandardCharsets.UTF_8)) {
            var data=new Gson().fromJson(reader,DimensionParticles.Data.class);
            DimensionParticles.validate(data,"ssjj_zeus:hand_of_zeuss2_focus","world","ssjj_zeus:textures/effects/");
            if(data.emitters.length!=7)throw new IllegalArgumentException("Missing original jingti layers");
            for(var emitter:data.emitters)if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())throw new IllegalArgumentException("Missing jingti texture");
            source=data;
            LogUtils.getLogger().info("ZEUS_FOCUS loaded {} original jingti layers",data.emitters.length);
        }catch(Exception error){LogUtils.getLogger().error("Cannot load original Zeus focus effect",error);}
    }
    private static boolean ready() {
        if(source==null&&!fallbackAttempted) {
            read(Minecraft.getInstance().getResourceManager());
            fallbackAttempted=true;
        }
        return source!=null;
    }
    private ZeusFocusClient() {}
    private static void world() {
        var current=Minecraft.getInstance().level;
        if(current!=level){selected=null;level=current;}
    }
    public static void accept(ZeusAdventNetwork.Focus packet) {
        world();
        if(packet==null||!packet.valid()||level==null||!level.dimension().location().equals(packet.dimension()))return;
        if(selected==null||packet.targetId()!=selected.targetId()||!packet.targetUUID().equals(selected.targetUUID()))markedAt=level.getGameTime();
        selected=packet.targetId()<0?null:packet;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        world();
        if(level==null||selected==null||!level.dimension().location().equals(selected.dimension()))return;
        var target=level.getEntity(selected.targetId());
        if(target==null||!selected.targetUUID().equals(target.getUUID())||target.isRemoved()||!target.isAlive()||target.isSpectator())return;
        var mc=Minecraft.getInstance();
        if(mc.player==null||!ready())return;
        var camera=event.getCamera().getPosition();float partial=event.getPartialTick();
        double x=Mth.lerp(partial,target.xOld,target.getX());
        double y=Mth.lerp(partial,target.yOld,target.getY())+target.getBbHeight()+.35;
        double z=Mth.lerp(partial,target.zOld,target.getZ());
        var pose=event.getPoseStack();pose.pushPose();
        pose.translate(x-camera.x,y-camera.y,z-camera.z);
        var rotation=event.getCamera().rotation();
        var right=rotation.transform(new Vector3f(1,0,0));var up=rotation.transform(new Vector3f(0,1,0));
        float seconds=(level.getGameTime()-markedAt+partial)/20f;
        ZeusEntityEffectRenderer.draw(source,seconds,right,up,pose);
        pose.popPose();ZeusEntityEffectRenderer.flush();
    }
}
