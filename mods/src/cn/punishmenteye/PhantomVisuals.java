package cn.punishmenteye;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Frozen skin and limb pose, world-space position, fade over 0.5s. No live entity re-render. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID,value=Dist.CLIENT)
public final class PhantomVisuals {
    private record Pose(PartPose[] parts,boolean[] visible,int tick){}
    private record Ghost(UUID owner,ResourceLocation skin,PlayerModel<AbstractClientPlayer> model,double x,double y,double z,float yaw,int born){}
    private static final Deque<Ghost> GHOSTS=new ArrayDeque<>();
    private static final Map<UUID,Pose> POSES=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static int clock;
    static ModelPart[] parts(PlayerModel<?> m){return new ModelPart[]{m.head,m.hat,m.body,m.leftArm,m.rightArm,m.leftLeg,m.rightLeg,m.leftSleeve,m.rightSleeve,m.leftPants,m.rightPants,m.jacket};}
    @SubscribeEvent public static void capture(RenderPlayerEvent.Post e){
        ModelPart[] parts=parts(e.getRenderer().getModel());PartPose[] poses=new PartPose[parts.length];boolean[] visible=new boolean[parts.length];
        for(int i=0;i<parts.length;i++){poses[i]=parts[i].storePose();visible[i]=parts[i].visible;}
        POSES.put(e.getEntity().getUUID(),new Pose(poses,visible,clock));
    }
    public static void accept(PhantomNetwork.Snapshot s){
        var mc=Minecraft.getInstance();
        if(mc.level==null||!mc.level.dimension().location().equals(s.dimension()))return;
        if(!(mc.level.getEntity(s.id()) instanceof AbstractClientPlayer p)||!p.getUUID().equals(s.uuid())||!p.isAlive()||p.isInvisible()||p.isSpectator())return;
        if(level!=mc.level){GHOSTS.clear();POSES.clear();level=mc.level;}
        // Walking ghosts are suppressed for sleep/swim/flight rather than drawing an upright duplicate.
        if(p.isSleeping()||p.isVisuallySwimming()||p.isFallFlying())return;
        boolean slim="slim".equals(p.getModelName());
        var model=new PlayerModel<AbstractClientPlayer>(PlayerModel.createMesh(CubeDeformation.NONE,slim).getRoot().bake(64,64),slim);
        Pose saved=POSES.get(s.uuid());
        if(saved!=null&&clock-saved.tick<=4){
            var parts=parts(model);for(int i=0;i<parts.length;i++){parts[i].loadPose(saved.parts[i]);parts[i].visible=saved.visible[i];}
        }else{
            model.crouching=p.isCrouching();model.young=false;
            model.prepareMobModel(p,p.walkAnimation.position(),p.walkAnimation.speed(),0);
            model.setupAnim(p,p.walkAnimation.position(),p.walkAnimation.speed(),p.tickCount,p.getYHeadRot()-s.bodyYaw(),s.pitch());
        }
        model.young=false;
        GHOSTS.addLast(new Ghost(s.uuid(),p.getSkinTextureLocation(),model,s.x(),s.y(),s.z(),s.bodyYaw(),clock));
        while(GHOSTS.size()>PassiveRules.MAX_PHANTOMS)GHOSTS.removeFirst();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(level!=mc.level){GHOSTS.clear();POSES.clear();level=mc.level;}
        if(mc.isPaused())return;
        clock++;
        GHOSTS.removeIf(g->clock-g.born>=PassiveRules.PHANTOM_TICKS||mc.level==null||mc.level.getPlayerByUUID(g.owner)==null||!mc.level.getPlayerByUUID(g.owner).isAlive());
        POSES.entrySet().removeIf(e2->clock-e2.getValue().tick>20);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES||GHOSTS.isEmpty())return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.level!=level)return;
        var camera=e.getCamera().getPosition();PoseStack pose=e.getPoseStack();
        var buffers=mc.renderBuffers().bufferSource();
        // Furthest first for translucent blending; depth testing remains enabled (no through-wall image).
        var sorted=new ArrayList<>(GHOSTS);sorted.sort(Comparator.comparingDouble((Ghost g)->camera.distanceToSqr(g.x,g.y,g.z)).reversed());
        for(Ghost g:sorted){
            var player=mc.level.getPlayerByUUID(g.owner);if(player==null||player.isInvisible()||player.isSpectator())continue;
            boolean cameraOwner=mc.getCameraEntity()!=null&&g.owner.equals(mc.getCameraEntity().getUUID());
            if(!PhantomVisibility.visible(mc.options.getCameraType().isFirstPerson(),cameraOwner,camera.x-g.x,camera.y-g.y,camera.z-g.z,player.getBbHeight()))continue;
            float alpha=PassiveRules.alpha(clock-g.born+e.getPartialTick());if(alpha<=0)continue;
            pose.pushPose();pose.translate(g.x-camera.x,g.y-camera.y,g.z-camera.z);pose.mulPose(Axis.YP.rotationDegrees(180-g.yaw));pose.scale(-0.9375f,-0.9375f,0.9375f);pose.translate(0,-1.501,0);
            var type=RenderType.entityTranslucent(g.skin);
            g.model.renderToBuffer(pose,buffers.getBuffer(type),15728880,OverlayTexture.NO_OVERLAY,0.48f,0.68f,1f,alpha);
            pose.popPose();buffers.endBatch(type);
        }
    }
}
