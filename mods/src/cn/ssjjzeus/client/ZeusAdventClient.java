package cn.ssjjzeus.client;

import cn.ssjjzeus.ZeusAdventNetwork;
import cn.ssjjzeus.ZeusEnergy;
import cn.ssjjzeus.ZeusThunder;
import cn.ssjjzeus.ZeusConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.TimelessAPI;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.multiplayer.ClientLevel;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only snapshots. A phantom is never added to a world/entity/target list. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class ZeusAdventClient {
    private static final ResourceLocation LIGHTNING=new ResourceLocation("ssjj_zeus","textures/advent/source_lightning.png");
    private static final Map<UUID,Visual> GHOSTS=new LinkedHashMap<>();
    private static final Map<Integer,ZeusAdventNetwork.Status> STATUS=new HashMap<>();
    private static final Map<Integer,Integer> STATUS_AT=new HashMap<>();
    private static ClientLevel level;
    private static int clock;
    private static int pendingCast=-1,castDeadline;
    // Native mesh baking is independent of owner/skin. Poses are applied just
    // before each sequential draw; never share a live player's animated model.
    private static PlayerModel<AbstractClientPlayer> wideModel,slimModel;
    private static PlayerModel<AbstractClientPlayer> model(boolean slim) {
        var cached=slim?slimModel:wideModel;
        if(cached==null) {
            cached=new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE,slim).getRoot().bake(64,64),slim);
            cached.young=false;cached.crouching=false;
            if(slim)slimModel=cached;else wideModel=cached;
        }
        return cached;
    }
    private static final class Visual {
        ZeusAdventNetwork.Ghost packet;
        final ResourceLocation skin;
        final PlayerModel<AbstractClientPlayer> model;
        final AABB bounds;
        final String profile;
        int updated;
        Visual(ZeusAdventNetwork.Ghost packet,AbstractClientPlayer player) {
            this.packet=packet;updated=clock;
            var p=packet.position();bounds=new AABB(p.x-1.6,p.y-.3,p.z-1.6,p.x+1.6,p.y+2.6,p.z+1.6);
            profile=ZeusThunder.profile(packet.weapon(),ZeusEnergy.GUN);
            boolean slim=player==null?"slim".equals(DefaultPlayerSkin.getSkinModelName(packet.owner())):"slim".equals(player.getModelName());
            skin=player==null?DefaultPlayerSkin.getDefaultSkin(packet.owner()):player.getSkinTextureLocation();
            model=model(slim);
        }
    }
    private ZeusAdventClient() {}
    private static void world() {
        var current=Minecraft.getInstance().level;
        if(current!=level){GHOSTS.clear();STATUS.clear();STATUS_AT.clear();clock=0;pendingCast=-1;wideModel=slimModel=null;ZeusPhantomWeapon.clear();level=current;}
    }
    public static void ghost(ZeusAdventNetwork.Ghost packet) {
        world();if(packet==null||!packet.valid())return;
        if(packet.remove()){GHOSTS.remove(packet.id());return;}
        if(level==null||!level.dimension().location().equals(packet.dimension()))return;
        Visual visual=GHOSTS.get(packet.id());
        if(visual==null) {
            visual=new Visual(packet,level.getPlayerByUUID(packet.owner()) instanceof AbstractClientPlayer p?p:null);
            GHOSTS.put(packet.id(),visual);
        }else{visual.packet=packet;visual.updated=clock;}
        while(GHOSTS.size()>512)GHOSTS.remove(GHOSTS.keySet().iterator().next());
        String profile=ZeusThunder.profile(packet.weapon(),ZeusEnergy.GUN);
        if(packet.summoned())ZeusThunderClient.playSummon(packet.position(),profile);
        if(packet.shot())ZeusThunderClient.playShot(packet.position(),profile);
    }
    public static void status(ZeusAdventNetwork.Status packet) {
        world();if(packet==null||!packet.valid()||level==null||!level.dimension().location().equals(packet.dimension()))return;
        STATUS.put(packet.slot(),packet);STATUS_AT.put(packet.slot(),clock);
        var mc=Minecraft.getInstance();
        if(packet.cast()&&mc.player!=null)ZeusAdventKeys.castStarted();
        if(packet.cast()&&mc.player!=null&&mc.player.getInventory().selected==packet.slot()&&ZeusEnergy.tierII(mc.player.getMainHandItem())) {
            pendingCast=packet.slot();castDeadline=clock+40;playCast();
        }
    }
    private static void playCast() {
        var mc=Minecraft.getInstance();if(pendingCast<0||mc.player==null)return;
        if(clock>castDeadline||mc.player.getInventory().selected!=pendingCast||!ZeusEnergy.tierII(mc.player.getMainHandItem())){pendingCast=-1;return;}
        TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).ifPresent(display->{
            var machine=display.getAnimationStateMachine();
            if(machine!=null&&machine.isInitialized()&&machine.getContext()!=null&&machine.getContext().hasAnimationPrototype("zeus_advent")) {
                machine.trigger("zeus_advent");ZeusAdventKeys.animationStarted();pendingCast=-1;
            }
        });
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;world();
        if(level==null||Minecraft.getInstance().isPaused())return;
        clock++;playCast();GHOSTS.values().removeIf(v->clock-v.updated>=v.packet.remainingTicks());
    }
    private static ResourceLocation ui(String name){return new ResourceLocation("ssjj_zeus","textures/gui/advent/"+name+".png");}
    public record HudLayout(float x,float y,float scale) {}
    /** Same lower-right anchor as the existing source 210x90 skill cards.
     * TaCZ's weapon icon begins at width-117; preserve an eight-pixel gap. */
    public static HudLayout hudLayout(int width,int height) {
        float scale=Math.min(.5f,Math.max(.05f,(width-129f)/210f));
        return new HudLayout(Math.max(4,width-125-210*scale),Math.max(16,height-24-90*scale),scale);
    }
    public static boolean skillReady(int charges){return charges>0;}
    public static float recoveryProgress(int charges,int capacity,int remaining,int elapsed) {
        return recoveryProgress(charges,capacity,remaining,elapsed,800);
    }
    public static float recoveryProgress(int charges,int capacity,int remaining,int elapsed,int period){return charges>=capacity?1:Math.max(0,Math.min(1,1-(remaining-Math.max(0,elapsed))/(float)Math.max(1,period)));}
    @SubscribeEvent public static void hud(RenderGuiOverlayEvent.Post event) {
        if(!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id()))return;
        world();var mc=Minecraft.getInstance();
        if(!ZeusConfig.enabled("advent_enabled")||mc.player==null||mc.options.hideGui||mc.player.isSpectator()||!ZeusEnergy.tierII(mc.player.getMainHandItem()))return;
        int slot=mc.player.getInventory().selected;var state=STATUS.get(slot);if(state==null)return;
        var graphics=event.getGuiGraphics();var layout=hudLayout(graphics.guiWidth(),graphics.guiHeight());int x=0,y=0;
        graphics.pose().pushPose();graphics.pose().translate(layout.x(),layout.y(),0);graphics.pose().scale(layout.scale(),layout.scale(),1);
        // GuiGraphics.blit does not enable blending. Source transparent RGB
        // must never be painted as solid black quads or compression specks.
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        var texture=ui(skillReady(state.charges())?"activebg":"unactivebg");
        // Preserve the supplied hint panel, replacing its original E glyph with
        // the bound summon key. The extra numeric phantom label stays removed.
        graphics.blit(texture,x,y,0,0,96,90,210,90);
        graphics.blit(texture,x+104,y,104,0,106,90,210,90);
        graphics.blit(texture,x+96,y,96,0,8,41,210,90);
        graphics.blit(texture,x+96,y+54,96,54,8,36,210,90);
        for(int row=41;row<54;row++)graphics.blit(texture,x+96,y+row,96,35,8,1,210,90);
        Component key=ZeusAdventKeys.skillKeyLabel();float fit=Math.min(1,8f/Math.max(1,mc.font.width(key)));
        graphics.pose().pushPose();graphics.pose().translate(x+97,y+43,0);graphics.pose().scale(fit,1,1);
        graphics.drawString(mc.font,key,0,0,0x15def1,false);graphics.pose().popPose();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(1,1,1,1);
        int slots=phantomSlots(state.maxActive()),active=phantomIcons(state.active(),slots);
        float iconScale=Math.min(1,125f/(slots*22));
        for(int i=0;i<slots;i++){
            graphics.pose().pushPose();graphics.pose().translate(x+76+i*22*iconScale,y+4,0);graphics.pose().scale(iconScale,iconScale,1);
            graphics.blit(ui(i<active?"activeicon":"unactiveicon"),0,0,0,0,22,22,22,22);graphics.pose().popPose();
        }
        int elapsed=Math.max(0,clock-STATUS_AT.getOrDefault(slot,clock));
        float progress=recoveryProgress(state.charges(),state.maxCharges(),state.rechargeTicks(),elapsed,state.rechargePeriod());
        // The original strip remains visible at zero progress; the source
        // pixels are dimmed for the empty track and drawn normally for its fill.
        RenderSystem.setShaderColor(.25f,.25f,.25f,.6f);
        graphics.blit(ui("normalprogress"),x+76,y+62,0,0,125,7,125,7);
        RenderSystem.setShaderColor(1,1,1,1);
        int width=Math.round(125*progress);if(width>0)graphics.blit(ui("normalprogress"),x+76,y+62,0,0,width,7,125,7);
        RenderSystem.disableBlend();graphics.pose().popPose();
    }
    /** The icon row describes the active population, never stored charges. */
    public static int phantomSlots(int maximum){return Math.max(1,Math.min(16,maximum));}
    public static int phantomIcons(int active,int slots){return Math.max(0,Math.min(phantomSlots(slots),active));}

    private static void pose(Visual visual) {
        var model=visual.model;float pitch=(float)Math.toRadians(visual.packet.pitch());
        model.head.xRot=pitch;model.head.yRot=0;model.hat.copyFrom(model.head);
        // Native player mesh with a fixed two-hand gun stance; only head/arm aim
        // follows server shot angles. No locomotion or live entity is rendered.
        model.rightArm.xRot=-1.48f+pitch;model.rightArm.yRot=-.12f;
        model.leftArm.xRot=-1.35f+pitch;model.leftArm.yRot=.35f;
        model.rightSleeve.copyFrom(model.rightArm);model.leftSleeve.copyFrom(model.leftArm);
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        world();if(level==null||GHOSTS.isEmpty())return;
        var mc=Minecraft.getInstance();var camera=event.getCamera().getPosition();var buffers=mc.renderBuffers().bufferSource();
        var sorted=new ArrayList<Visual>();
        for(Visual visual:GHOSTS.values()) {
            Vec3 p=visual.packet.position();
            if(visual.packet.remainingTicks()-(clock-visual.updated)<=0||camera.distanceToSqr(p)>96*96||!event.getFrustum().isVisible(visual.bounds))continue;
            if(mc.options.getCameraType().isFirstPerson()&&Math.abs(camera.x-p.x)<.65&&Math.abs(camera.z-p.z)<.65&&camera.y>p.y&&camera.y<p.y+2.1)continue;
            sorted.add(visual);
        }
        if(sorted.isEmpty())return;
        sorted.sort(Comparator.comparingDouble((Visual v)->camera.distanceToSqr(v.packet.position())).reversed());
        PoseStack pose=event.getPoseStack();
        // The stationary source gun is uploaded once. No complete item renderer,
        // attachment traversal, Java gun-vertex loop or gun-particle sampling per phantom.
        ZeusPhantomWeapon.beginBatch();
        try {for(Visual visual:sorted) {
            pose(visual);worldPose(pose,visual,camera);
            visual.model.translateToHand(HumanoidArm.RIGHT,pose);pose.mulPose(Axis.XP.rotationDegrees(-90));pose.mulPose(Axis.YP.rotationDegrees(180));pose.translate(.0625,.125,-.625);
            ZeusPhantomWeapon.draw(pose,visual.profile);pose.popPose();
        }} finally {ZeusPhantomWeapon.endBatch();}
        Set<RenderType> skinTypes=new LinkedHashSet<>();
        for(Visual visual:sorted) {
            pose(visual);worldPose(pose,visual,camera);float alpha=Math.min(1,(visual.packet.remainingTicks()-(clock-visual.updated))/8f);
            var skin=RenderType.entityTranslucent(visual.skin);skinTypes.add(skin);
            visual.model.renderToBuffer(pose,buffers.getBuffer(skin),15728880,OverlayTexture.NO_OVERLAY,1,.8f,.4f,.36f*alpha);
            pose.popPose();
        }
        for(var skin:skinTypes)buffers.endBatch(skin);
        // One shared source-lightning material and one final flush for all visible phantoms.
        var lightning=RenderType.energySwirl(LIGHTNING,(clock+event.getPartialTick())*.009f,0);
        for(Visual visual:sorted) {
            pose(visual);worldPose(pose,visual,camera);pose.scale(1.008f,1.008f,1.008f);
            float alpha=Math.min(1,(visual.packet.remainingTicks()-(clock-visual.updated))/8f);
            visual.model.renderToBuffer(pose,buffers.getBuffer(lightning),15728880,OverlayTexture.NO_OVERLAY,1,.62f,.08f,.8f*alpha);
            pose.popPose();
        }
        buffers.endBatch(lightning);
    }
    private static void worldPose(PoseStack pose,Visual visual,Vec3 camera) {
        Vec3 p=visual.packet.position();pose.pushPose();pose.translate(p.x-camera.x,p.y-camera.y,p.z-camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(180-visual.packet.yaw()));pose.scale(-.9375f,-.9375f,.9375f);pose.translate(0,-1.501,0);
    }
}
