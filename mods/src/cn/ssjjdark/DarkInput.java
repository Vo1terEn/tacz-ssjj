package cn.ssjjdark;

import cn.judgfire.BurstStun;
import com.mojang.blaze3d.platform.InputConstants;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.client.input.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Vanilla mappings retain rebindings; the TaCZ interact mapping retains its native use path. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkInput {
    private static final java.util.Set<net.minecraft.client.KeyMapping> GATED=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private static java.util.List<net.minecraft.client.KeyMapping> nativeKeys;
    private static long clientTick,lastRequest;
    private static boolean requested,lastHeavy;
    private static boolean releaseRequired;
    private static boolean heavyPressConsumed;
    private static ItemStack automaticStack;
    private static ItemStack automaticMigration;
    private static int automaticSlot;
    private DarkInput(){}
    private static boolean held(Minecraft mc){return mc.player!=null&&DarkCombat.matches(mc.player.getMainHandItem());}
    private static boolean playable(Minecraft mc){
        return held(mc)&&DarkCore.ready(mc.player.getMainHandItem())&&mc.level!=null&&mc.screen==null&&mc.getOverlay()==null&&!mc.isPaused()
            &&mc.isWindowActive()&&mc.mouseHandler.isMouseGrabbed()&&mc.player.isAlive()&&!mc.player.isSpectator()
            &&!BurstStun.stunned(mc.player)&&!cn.ssjjdark.client.DarkLiberationClient.charging();
    }
    private static void stopAutomatic(){automaticStack=null;automaticMigration=null;}
    private static void interruptAutomatic(Minecraft mc){
        stopAutomatic();if(mc.options!=null&&mc.options.keyAttack.isDown())releaseRequired=true;
    }
    private static boolean boundAutomatic(Minecraft mc){
        if(mc.player.getInventory().selected!=automaticSlot)return false;
        if(mc.player.getMainHandItem()==automaticStack)return true;
        // Permit only the first server replacement for an old SEMI carrier,
        // with the exact expected AUTO tag, item and count.
        if(automaticMigration==null||!ItemStack.matches(automaticMigration,mc.player.getMainHandItem()))return false;
        automaticStack=mc.player.getMainHandItem();automaticMigration=null;return true;
    }
    private static void request(Minecraft mc,boolean heavy){
        if(requested&&clientTick-lastRequest<DarkDamageRules.intervalTicks(lastHeavy,heavy))return;
        DarkCombat.request(mc.player.getInventory().selected,heavy);
        requested=true;lastRequest=clientTick;lastHeavy=heavy;
    }
    private static void suppressNative(Minecraft mc){
        // Inspect and refit deliberately keep their ordinary native dispatch.
        // Actual TaCZ 1.2 dispatches mouse/keyboard presses immediately through
        // isActiveAndMatches, before the tick poll. Gate its native actions once
        // while retaining the original key codes, modifiers and other wrappers.
        if(nativeKeys==null){
            nativeKeys=new java.util.ArrayList<>(java.util.List.of(ShootKey.SHOOT_KEY,AimKey.AIM_KEY,MeleeKey.MELEE_KEY,FireSelectKey.FIRE_SELECT_KEY));
        }
        for(var key:nativeKeys){
            if(GATED.add(key))key.setKeyConflictContext(new Context(key.getKeyConflictContext()));
        }
        if(!held(mc))return;
        for(var key:new net.minecraft.client.KeyMapping[]{ShootKey.SHOOT_KEY,AimKey.AIM_KEY,MeleeKey.MELEE_KEY}){
            key.setDown(false);while(key.consumeClick()){}
        }
        var operator=IClientPlayerGunOperator.fromLocalPlayer(mc.player);if(operator.isAim())operator.aim(false);
    }
    private static final class Context implements IKeyConflictContext {
        private final IKeyConflictContext original;
        Context(IKeyConflictContext original){this.original=original;}
        public boolean isActive(){return !held(Minecraft.getInstance())&&original.isActive();}
        public boolean conflicts(IKeyConflictContext other){return original.conflicts(other instanceof Context context?context.original:other);}
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeDarkCrystalTick(TickEvent.ClientTickEvent event){
        if(event.phase==TickEvent.Phase.START)clientTick++;
        suppressNative(Minecraft.getInstance());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void afterDarkCrystalTick(TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();suppressNative(mc);
        if(event.phase!=TickEvent.Phase.END)return;
        if(mc.options==null||!mc.options.keyUse.isDown())heavyPressConsumed=false;
        else if(!playable(mc))heavyPressConsumed=true;
        if(mc.options==null||!mc.options.keyAttack.isDown()){releaseRequired=false;stopAutomatic();return;}
        if(!playable(mc)||InteractKey.INTERACT_KEY.isDown()){interruptAutomatic(mc);return;}
        if(automaticStack==null)return;
        if(!boundAutomatic(mc)){interruptAutomatic(mc);return;}
        request(mc,false);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void interactDarkCrystal(InputEvent.InteractionKeyMappingTriggered event){
        var mc=Minecraft.getInstance();if(!held(mc)||!event.isAttack()&&!event.isUseItem())return;
        // TaCZ calls Minecraft.startUseItem for its separately bound interact
        // action. Its own click blocker also allows that mapping while down;
        // turning this event into a heavy strike would swallow block menus.
        if(event.isUseItem()&&InteractKey.INTERACT_KEY.isDown()){interruptAutomatic(mc);return;}
        event.setCanceled(true);event.setSwingHand(false);
        if(RefitKey.REFIT_KEY.isDown()){interruptAutomatic(mc);return;}
        if(!DarkCore.ready(mc.player.getMainHandItem())){
            mc.player.displayClientMessage(Component.translatable("message.ssjj_dark.core_required"),true);
            interruptAutomatic(mc);return;
        }
        if(!playable(mc)){interruptAutomatic(mc);if(event.isUseItem())heavyPressConsumed=true;return;}
        if(event.isAttack()){
            // Minecraft.continueAttack also produces this event every held tick
            // against blocks. It must not rearm a hold invalidated by a swap/UI.
            if(releaseRequired)return;
            if(automaticStack!=null&&!boundAutomatic(mc)){interruptAutomatic(mc);return;}
            if(automaticStack==null){
                automaticStack=mc.player.getMainHandItem();automaticSlot=mc.player.getInventory().selected;
                automaticMigration=automaticStack.copy();DarkCombat.ensureAutomatic(automaticMigration);
                if(ItemStack.matches(automaticMigration,automaticStack))automaticMigration=null;
            }
            request(mc,false);
        }else if(event.getHand()==InteractionHand.MAIN_HAND){
            if(heavyPressConsumed)return;
            heavyPressConsumed=true;request(mc,true);
        }
        suppressNative(mc);
    }
    private static void auxiliaryPress(InputConstants.Key input,int action){
        var mc=Minecraft.getInstance();if(action!=1||!held(mc)||cn.ssjjdark.client.DarkLiberationClient.charging())return;
        suppressNative(mc);
        if(InspectKey.INSPECT_KEY.isActiveAndMatches(input))DarkInspect.beforeNativeInspect();
        // The refit key keeps TaCZ's original dispatch and screen. Stop held
        // combat before that screen is opened, including overlapping rebindings.
        if(RefitKey.REFIT_KEY.isActiveAndMatches(input)){
            interruptAutomatic(mc);
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void auxiliaryDarkCrystalMouse(InputEvent.MouseButton.Post event){
        auxiliaryPress(InputConstants.Type.MOUSE.getOrCreate(event.getButton()),event.getAction());
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void auxiliaryDarkCrystalKey(InputEvent.Key event){
        auxiliaryPress(InputConstants.getKey(event.getKey(),event.getScanCode()),event.getAction());
    }
    // Release-only observation sends no actions. It also catches a quick
    // release/repress between ticks, including rebound mouse/keyboard mappings.
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void mouseDarkCrystalRelease(InputEvent.MouseButton.Pre event){
        var mc=Minecraft.getInstance();if(event.getAction()==0&&mc.options!=null&&mc.options.keyAttack.matchesMouse(event.getButton())){
            mc.options.keyAttack.setDown(false);releaseRequired=false;stopAutomatic();
        }
        if(event.getAction()==0&&mc.options!=null&&mc.options.keyUse.matchesMouse(event.getButton())){
            mc.options.keyUse.setDown(false);heavyPressConsumed=false;
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void keyDarkCrystalRelease(InputEvent.Key event){
        var mc=Minecraft.getInstance();if(event.getAction()==0&&mc.options!=null&&mc.options.keyAttack.matches(event.getKey(),event.getScanCode())){
            mc.options.keyAttack.setDown(false);releaseRequired=false;stopAutomatic();
        }
        if(event.getAction()==0&&mc.options!=null&&mc.options.keyUse.matches(event.getKey(),event.getScanCode())){
            mc.options.keyUse.setDown(false);heavyPressConsumed=false;
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tooltipDarkCrystal(ItemTooltipEvent event){
        if(!DarkCombat.matches(event.getItemStack()))return;
        var lines=event.getToolTip();var title=event.getItemStack().getHoverName();lines.clear();lines.add(title);
        lines.add(Component.literal("近战武器"));lines.add(Component.literal("左键伤害：12"));lines.add(Component.literal("右键伤害：20"));
        lines.add(Component.literal("需装填黯影晶核，装填后攻击不消耗"));
        int tier=DarkWrath.tier(event.getItemStack());
        if(tier>=1){
            lines.add(Component.literal("【龙印斩】左/右键释放龙之势："+(tier>=2?"三方向，各20伤害":"单道，15伤害")));
            lines.add(Component.literal("【绝·怒意】物品栏携带生效；每损失10%最大生命获得1层"));
            lines.add(Component.literal("每层伤害+5%"+(tier>=2?"、减伤2%":"")+"；回血超过阈值时退层"));
            lines.add(Component.literal("水晶刀刃：本武器每层加成翻倍"));
        }
        if(tier>=2){
            lines.add(Component.literal("【绝·力量解放】V键；持续18秒后冷却6秒；消耗当前生命70%"));
            lines.add(Component.literal("蓄力3秒无法操作并击退敌人；结束造成范围伤害30"));
            lines.add(Component.literal("从施放起无敌5秒；18秒内移速+20%、怒意减伤及龙印斩伤害翻倍"));
            lines.add(Component.literal("18秒护盾上限为最大生命200%；扣除生命×2转盾，回血转盾"));
            lines.add(Component.literal("水晶核心：施放起无敌10秒，护盾上限400%，扣除生命×4转盾"));
            lines.add(Component.literal("技能结束清空护盾并恢复正常回血"));
            lines.add(Component.literal("终极强化石／创造强化石：18秒锁定1生命、怒意伤害+99%"));
            lines.add(Component.literal("施放时可自由操作；3秒后爆炸；结束生命恢复至上限30%"));
        }
    }
}
