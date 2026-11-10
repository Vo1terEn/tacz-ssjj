package cn.ssjjdimension.client;

import cn.ssjjdimension.DimensionFieldNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.event.common.GunShootEvent;
import com.tacz.guns.client.input.ShootKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** V requests the server skill; high-energy mouse input replaces normal fire. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionFieldClient {
    private static final KeyMapping KEY=new KeyMapping("key.ssjj_dimension.field",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_V,"key.categories.eyeofpunishment");
    private static net.minecraft.client.multiplayer.ClientLevel level;
    private static DimensionFieldNetwork.Status status;
    private static long received,lastCast=-100,lastActivate=-100;
    private static boolean leftHeld;
    private static long activationStarted;
    private static boolean skillGesture;
    private DimensionFieldClient() {}
    @Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void registerDimensionFieldKey(RegisterKeyMappingsEvent e){e.register(KEY);}
    }
    public static int tier(ItemStack stack){
        var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);if(gun==null)return 0;
        var id=gun.getGunId(stack);if(id==null||!"ssjj_dimension".equals(id.getNamespace()))return 0;
        return switch(id.getPath()){case "dimension_star1"->1;case "dimension_star2"->2;default->0;};
    }
    public static int heldTier(){var p=Minecraft.getInstance().player;return p==null?0:tier(p.getMainHandItem());}
    public static Component skillKeyLabel(){return KEY.getTranslatedKeyMessage();}
    public static int fieldCount(){return status==null?0:status.count();}
    private static boolean enabled(){
        var mc=Minecraft.getInstance();return mc.player!=null&&mc.level!=null&&mc.screen==null&&mc.getOverlay()==null&&!mc.isPaused()&&mc.isWindowActive()
            &&mc.mouseHandler.isMouseGrabbed()&&mc.player.isAlive()&&!mc.player.isSpectator()&&heldTier()>0;
    }
    public static boolean reserveSkillKey(InputEvent.Key event){
        var mc=Minecraft.getInstance();return mc.player!=null&&mc.screen==null&&heldTier()>0&&event.getAction()==GLFW.GLFW_PRESS&&KEY.matches(event.getKey(),event.getScanCode());
    }
    public static int activeTicks(){
        var mc=Minecraft.getInstance();if(status==null||mc.level!=level||mc.player==null||heldTier()==0||mc.player.getInventory().selected!=status.slot())return 0;
        return Math.max(0,status.activeTicks()-(int)(level.getGameTime()-received));
    }
    public static int cooldownTicks(){return status==null||level==null?0:Math.max(0,status.cooldownTicks()-(int)(level.getGameTime()-received));}
    /** Presentation clock starts at the server's high-energy activation, never at resource load. */
    public static float activationSeconds(float partial){int ticks=activeTicks();return ticks<=0?-1:(level.getGameTime()-activationStarted+Math.max(0,Math.min(1,partial)))/20f;}
    public static void receive(DimensionFieldNetwork.Status next){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!next.valid()||!next.dimension().equals(mc.level.dimension().location()))return;
        boolean wasActive=activeTicks()>0;
        boolean heldBeforeActivation=ShootKey.SHOOT_KEY.isDown()||mc.options.keyAttack.isDown();
        level=mc.level;status=next;received=level.getGameTime();
        if(!wasActive&&activeTicks()>0){
            lastCast=received+next.castWait()-cn.ssjjdimension.DimensionFieldRules.castInterval();leftHeld=heldBeforeActivation;activationStarted=received;
            if(actionReady()){animation("inspect_break_out");animation("dimension_field_start");skillGesture=true;}
        }
        if(wasActive&&next.activeTicks()==0){animation("dimension_field_end");releaseFire();}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void key(InputEvent.Key e){
        if(!reserveSkillKey(e)||!enabled()||(activeTicks()==0&&cooldownTicks()>=cn.ssjjdimension.DimensionFieldRules.recharge()))return;
        long now=Minecraft.getInstance().level.getGameTime();if(now-lastActivate<1)return;lastActivate=now;
        DimensionFieldNetwork.request(Minecraft.getInstance().player.getInventory().selected,false);
    }
    private static void animation(String input){
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).ifPresent(d->{var m=d.getAnimationStateMachine();if(m!=null&&m.isInitialized())m.trigger(input);});
    }
    private static boolean actionReady(){
        var player=Minecraft.getInstance().player;if(player==null)return false;
        return DimensionFieldInputRules.actionReady(com.tacz.guns.api.entity.IGunOperator.fromLivingEntity(player),com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(player).getDataHolder());
    }
    private static void cast(){
        var mc=Minecraft.getInstance();if(!enabled()||activeTicks()==0||!actionReady())return;
        long now=mc.level.getGameTime();if(now-lastCast<cn.ssjjdimension.DimensionFieldRules.castInterval())return;lastCast=now;
        DimensionFieldNetwork.request(mc.player.getInventory().selected,true);
        TimelessAPI.getGunDisplay(mc.player.getMainHandItem()).ifPresent(d->{var machine=d.getAnimationStateMachine();if(machine!=null&&machine.isInitialized()){machine.trigger("inspect_break_out");machine.trigger("shoot");}});
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void mouse(InputEvent.MouseButton.Pre e){
        if(e.getButton()!=GLFW.GLFW_MOUSE_BUTTON_LEFT)return;
        if(e.getAction()==GLFW.GLFW_RELEASE)leftHeld=false;
        if(!enabled()||activeTicks()==0)return;
        e.setCanceled(true);
        if(e.getAction()==GLFW.GLFW_PRESS){leftHeld=true;cast();}
        suppressFire();
    }
    private static void suppressFire(){
        var mc=Minecraft.getInstance();ShootKey.SHOOT_KEY.setDown(false);mc.options.keyAttack.setDown(false);
    }
    private static void releaseFire(){
        // A held button resumes native firing when the high-energy state finishes.
        if(leftHeld&&enabled()){ShootKey.SHOOT_KEY.setDown(true);Minecraft.getInstance().options.keyAttack.setDown(true);}
        leftHeld=false;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeTick(TickEvent.ClientTickEvent e){
        var mc=Minecraft.getInstance();
        if(mc.level!=level){level=mc.level;status=null;leftHeld=false;lastCast=-100;lastActivate=-100;}
        if(skillGesture&&activeTicks()==0){animation("dimension_field_end");skillGesture=false;}
        if(activeTicks()>0)suppressFire();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void afterTick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END)return;
        if(!enabled()){leftHeld=false;return;}
        if(activeTicks()>0){suppressFire();if(leftHeld)cast();}
        else if(leftHeld)releaseFire();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void shoot(GunShootEvent e){
        if(e.getLogicalSide().isClient()&&e.getShooter()==Minecraft.getInstance().player&&activeTicks()>0)e.setCanceled(true);
    }
}
