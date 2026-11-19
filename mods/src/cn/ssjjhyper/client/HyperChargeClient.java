package cn.ssjjhyper.client;

import cn.judgfire.BurstClient;
import cn.ssjjhyper.HyperCharge;
import cn.ssjjhyper.HyperChargeNetwork;
import cn.ssjjhyper.HyperChargeRules;
import cn.ssjjhyper.HyperConfig;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;
import com.tacz.guns.client.input.AimKey;
import com.tacz.guns.client.input.InspectKey;
import com.tacz.guns.client.input.MeleeKey;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ShootKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Reuses the registered V key. Active left-button presses replace native fire. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperChargeClient {
    private static ClientLevel level;
    private static HyperChargeNetwork.Status status;
    private static ItemStack activeStack=ItemStack.EMPTY,requestedStack=ItemStack.EMPTY;
    private static java.util.UUID interruptedSession;
    private static long received,lastCast=-100,lastActivate=-100,rootUntil;
    private static float renderedFov=70;
    private HyperChargeClient() {}
    public static int tier(ItemStack stack) {
        var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);if(gun==null)return 0;
        var id=gun.getGunId(stack);if(id==null||!"ssjj_hyper".equals(id.getNamespace()))return 0;
        return switch(id.getPath()){case "hyper_revolver1"->1;case "hyper_revolver2"->2;default->0;};
    }
    public static int heldTier(){var p=Minecraft.getInstance().player;return p==null?0:tier(p.getMainHandItem());}
    public static Component skillKeyLabel(){return BurstClient.KEY.getTranslatedKeyMessage();}
    private static void world() {
        var next=Minecraft.getInstance().level;
        if(next!=level){HyperChargeAction.abandon();level=next;status=null;received=0;lastCast=-100;lastActivate=-100;rootUntil=0;activeStack=ItemStack.EMPTY;requestedStack=ItemStack.EMPTY;interruptedSession=null;}
        if(level!=null&&status!=null&&status.activeTicks()>0&&
            (!HyperConfig.enabled("charge_enabled")||HyperChargeClock.remaining(status.activeTicks(),received,level.getGameTime())==0||!bound())) {
            // Stop locally as soon as the exact acknowledged item is left. The next
            // server status corrects this prediction without reviving an old session.
            boolean switched=!bound();if(switched)HyperChargeAction.abandon();
            interruptedSession=status.session();
            status=new HyperChargeNetwork.Status(status.dimension(),status.slot(),0,HyperChargeRules.recoveryTicks(status.energy()),status.totalTicks(),status.energy(),0,status.cap(),status.profile(),status.session());
            received=level.getGameTime();activeStack=ItemStack.EMPTY;HyperChargeAction.receive(status);
            var player=Minecraft.getInstance().player;if(player!=null)HyperChargeNetwork.interrupt(player.getInventory().selected);
        }
        updateActionKeys(status!=null&&status.activeTicks()>0&&bound()&&HyperChargeClock.remaining(status.activeTicks(),received,level.getGameTime())>0);
    }
    /** Gate native press dispatch itself; key codes, mouse rebinds and modifiers remain native. */
    private static void updateActionKeys(boolean locked){
        HyperActionKeys.update(HyperActionKeys.CHARGE,locked,InspectKey.INSPECT_KEY,MeleeKey.MELEE_KEY,AimKey.AIM_KEY);
    }
    /** Cooldown belongs to the player, so it remains visible after changing eligible guns. */
    private static boolean cooldownBound() {
        var mc=Minecraft.getInstance();
        return status!=null&&mc.level==level&&level!=null&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&heldTier()>0&&status.dimension().equals(level.dimension().location());
    }
    private static boolean bound() {
        var mc=Minecraft.getInstance();int tier=heldTier();
        return status!=null&&mc.level==level&&level!=null&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&tier>0&&mc.player.getMainHandItem()==activeStack&&mc.player.getInventory().selected==status.slot()
            &&status.profile().startsWith("hyper_revolver"+tier)&&status.dimension().equals(level.dimension().location());
    }
    private static boolean enabled() {
        var mc=Minecraft.getInstance();return HyperConfig.enabled("charge_enabled")&&mc.player!=null&&mc.level!=null&&mc.screen==null&&mc.getOverlay()==null&&!mc.isPaused()
            &&mc.isWindowActive()&&mc.mouseHandler.isMouseGrabbed()&&mc.player.isAlive()&&!mc.player.isSpectator()&&heldTier()>0;
    }
    public static boolean reserveSkillKey(InputEvent.Key event) {
        var mc=Minecraft.getInstance();return HyperConfig.enabled("charge_enabled")&&mc.player!=null&&mc.screen==null&&heldTier()>0&&event.getAction()==GLFW.GLFW_PRESS
            &&BurstClient.KEY.matches(event.getKey(),event.getScanCode());
    }
    public static int activeTicks(){world();return bound()?HyperChargeClock.remaining(status.activeTicks(),received,level.getGameTime()):0;}
    public static int cooldownTicks(){world();return cooldownBound()?HyperChargeClock.remaining(status.cooldownTicks(),received,level.getGameTime()):0;}
    public static float cooldownRatio(float partial){world();return cooldownBound()?HyperChargeClock.cooldown(status.cooldownTicks(),received,level.getGameTime(),partial):0;}
    public static float energyRatio(float partial){
        world();
        if(bound()&&status.activeTicks()>0)return HyperChargeClock.energy(status.energy());
        return cooldownBound()?HyperChargeClock.recovery(status.energy(),status.cooldownTicks(),received,level.getGameTime(),partial):1;
    }
    public static int energy(){return Math.round(1000*energyRatio(0));}
    public static int marks(){world();return bound()?status.marks():0;}
    public static int cap(){world();return bound()?status.cap():0;}
    public static float durationRatio(float partial){world();return bound()?HyperChargeClock.duration(status.activeTicks(),status.totalTicks(),received,level.getGameTime(),partial):0;}
    public static void receive(HyperChargeNetwork.Status next) {
        var mc=Minecraft.getInstance();world();
        if(next==null||!next.valid()||level==null||mc.player==null||!next.dimension().equals(level.dimension().location()))return;
        if(next.activeTicks()>0&&next.session().equals(interruptedSession))return;
        if(next.activeTicks()>0&&(status==null||!next.session().equals(status.session()))){activeStack=requestedStack.isEmpty()?mc.player.getMainHandItem():requestedStack;requestedStack=ItemStack.EMPTY;}
        if(next.activeTicks()==0)activeStack=ItemStack.EMPTY;
        status=next;received=level.getGameTime();world();HyperChargeAction.receive(status);
        if(activeTicks()>0)suppressNativeActions();
    }
    public static void effect(HyperChargeNetwork.Visual next) {
        var mc=Minecraft.getInstance();world();
        if(next==null||!next.valid()||level==null||mc.player==null||!next.dimension().equals(level.dimension().location()))return;
        HyperChargeAudio.accept(next);
        if(next.type()==6) {
            if(next.owner().equals(mc.player.getUUID())&&next.targetUuid().equals(mc.player.getUUID())&&HyperChargeAction.attack(next))HyperChargeFlash.cast(next);
            return;
        }
        if(next.type()==5) {
            if(next.target()==mc.player.getId()&&next.targetUuid().equals(mc.player.getUUID()))
                rootUntil=next.life()==0?0:Math.max(rootUntil,next.tick()+next.life());
            return;
        }
        HyperChargeEffects.accept(next);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void fov(ViewportEvent.ComputeFov event) {
        // The hand pass also computes a fixed projection; only the configured
        // world projection represents targets visible inside the player's screen.
        if(!event.usedConfiguredFov())return;
        double value=event.getFOV();if(Double.isFinite(value)&&value>1&&value<179)renderedFov=(float)value;
    }
    private static void request(boolean cast) {
        var mc=Minecraft.getInstance();if(!cast)requestedStack=mc.player.getMainHandItem();float aspect=mc.getWindow().getWidth()/(float)Math.max(1,mc.getWindow().getHeight());
        HyperChargeNetwork.request(mc.player.getInventory().selected,cast,Math.max(10,Math.min(170,renderedFov)),Math.max(.25f,Math.min(6,aspect)));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(InputEvent.Key event) {
        if(!enabled())return;var mc=Minecraft.getInstance();long now=mc.level.getGameTime();
        if(reserveSkillKey(event)) {
            if(activeTicks()>0||cooldownTicks()>0||now-lastActivate<4)return;
            lastActivate=now;HyperChargeAction.requested();request(false);return;
        }
        if(activeTicks()>0&&AimKey.AIM_KEY.matches(event.getKey(),event.getScanCode()))suppressAim();
        // Honour a keyboard rebound attack key without adding an auto-fire loop.
        if(activeTicks()>0&&event.getAction()==GLFW.GLFW_PRESS&&ShootKey.SHOOT_KEY.matches(event.getKey(),event.getScanCode()))cast();
    }
    private static void cast() {
        if(!enabled()||activeTicks()==0)return;long now=level.getGameTime();
        if(now-lastCast<6)return;lastCast=now;request(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre event) {
        if(!enabled()||activeTicks()==0)return;
        boolean shoot=ShootKey.SHOOT_KEY.matchesMouse(event.getButton()),aim=AimKey.AIM_KEY.matchesMouse(event.getButton());
        if(!shoot&&!aim)return;
        event.setCanceled(true);if(shoot&&event.getAction()==GLFW.GLFW_PRESS)cast();suppressNativeActions();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        if((event.isAttack()||event.isUseItem())&&enabled()&&activeTicks()>0){event.setCanceled(true);event.setSwingHand(false);suppressNativeActions();}
    }
    /** Guard the native aim entry before it changes state or sends an aim packet.
     * TaCZ's legacy MouseButton.Post and controller paths do not consult the
     * key conflict context. The skill owns this player's hand pose until expiry.
     */
    public static boolean blocksAim(net.minecraft.world.entity.LivingEntity entity,boolean requested) {
        return requested&&entity!=null&&entity==Minecraft.getInstance().player&&activeTicks()>0;
    }
    private static void suppressFire(){var mc=Minecraft.getInstance();ShootKey.SHOOT_KEY.setDown(false);mc.options.keyAttack.setDown(false);}
    /** ADS transforms the whole hand model after animation, so the special pose owns it too. */
    private static void suppressAim(){
        var mc=Minecraft.getInstance();AimKey.AIM_KEY.setDown(false);while(AimKey.AIM_KEY.consumeClick()){}
        mc.options.keyUse.setDown(false);while(mc.options.keyUse.consumeClick()){}
        if(mc.player==null)return;
        var operator=IClientPlayerGunOperator.fromLocalPlayer(mc.player);if(operator==null)return;
        if(operator.isAim())operator.aim(false);
        var data=operator.getDataHolder();if(data!=null)data.clientAimingProgress=0;
        LocalPlayerDataHolder.oldAimingProgress=0;
    }
    private static void suppressNativeActions(){suppressFire();suppressAim();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeTick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.START)return;world();
        if(activeTicks()>0)suppressNativeActions();
    }
    /** Input can arrive after END tick; suppress the native ADS interpolation before this frame. */
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeRender(TickEvent.RenderTickEvent event) {
        if(event.phase!=TickEvent.Phase.START)return;world();
        if(activeTicks()>0)suppressNativeActions();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void afterTick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;world();HyperChargeAction.tick();
        if(Minecraft.getInstance().player==null||!Minecraft.getInstance().player.isAlive())rootUntil=0;
        if(activeTicks()>0)suppressNativeActions();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void shoot(GunShootEvent event) {
        if(event.getLogicalSide().isClient()&&event.getShooter()==Minecraft.getInstance().player&&activeTicks()>0)event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void melee(GunMeleeEvent event) {
        if(event.getLogicalSide().isClient()&&event.getShooter()==Minecraft.getInstance().player&&activeTicks()>0)event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void movement(MovementInputUpdateEvent event) {
        world();var mc=Minecraft.getInstance();
        if(level!=null&&event.getEntity()==mc.player&&mc.player!=null&&mc.player.isAlive()&&level.getGameTime()<rootUntil) {
            event.getInput().leftImpulse=0;event.getInput().forwardImpulse=0;event.getInput().jumping=false;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tooltip(ItemTooltipEvent event) { /* Skill descriptions intentionally omitted from weapon tooltips. */ }
}
