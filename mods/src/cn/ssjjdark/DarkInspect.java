package cn.ssjjdark;

import cn.fusionarsenal.compat.TaCZClientCompat;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Retain one native inspect press only while this exact draw is not ready. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkInspect {
    private record Pending(ClientLevel level,LocalPlayer player,ItemStack stack,ItemStack migration,int slot,int count,ResourceLocation gun,ResourceLocation display,long until) {}
    private static Pending pending;
    private static long tick;
    private DarkInspect() {}

    private static boolean inGame(Minecraft mc) {
        return mc.player!=null&&mc.level!=null&&mc.screen==null&&mc.getOverlay()==null&&!mc.isPaused()
            &&mc.isWindowActive()&&mc.mouseHandler.isMouseGrabbed()&&mc.player.isAlive()&&!mc.player.isSpectator();
    }
    private static ResourceLocation display(ItemStack stack) {
        var gun=IGun.getIGunOrNull(stack);
        if(gun==null||!DarkSkins.family(gun.getGunId(stack)))return null;
        var explicit=gun.getGunDisplayId(stack);
        return explicit!=null?explicit:DarkSkins.display(gun.getGunId(stack),DarkSkins.skin(gun.getAttachmentId(stack,com.tacz.guns.api.item.attachment.AttachmentType.SCOPE)));
    }
    private static boolean ready(Minecraft mc,ItemStack stack) {
        if(!TaCZClientCompat.prepareFirstPersonDraw(stack))return false;
        var machine=TimelessAPI.getGunDisplay(stack).map(d->d.getAnimationStateMachine()).orElse(null);
        if(machine==null||!machine.isInitialized()||machine.getContext()==null)return false;
        var data=IClientPlayerGunOperator.fromLocalPlayer(mc.player).getDataHolder();
        return !data.clientStateLock&&!TaCZClientCompat.isCharging(data);
    }
    /** Called before ordinary InspectKey dispatch; ready input stays native. */
    public static void beforeNativeInspect() {
        pending=null;var mc=Minecraft.getInstance();if(!inGame(mc))return;
        var stack=mc.player.getMainHandItem();var display=display(stack);if(display==null)return;
        if(ready(mc,stack))return;
        var migration=stack.copy();DarkCombat.ensureAutomatic(migration);DarkCore.prepare(migration);
        if(ItemStack.matches(migration,stack))migration=null;
        pending=new Pending(mc.level,mc.player,stack,migration,mc.player.getInventory().selected,stack.getCount(),IGun.getIGunOrNull(stack).getGunId(stack),display,tick+100);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void finishDarkCrystalDrawInspect(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        tick++;var held=pending;if(held==null)return;var mc=Minecraft.getInstance();
        if(!inGame(mc)||tick>held.until||mc.level!=held.level||mc.player!=held.player
            ||mc.player.getInventory().selected!=held.slot) {pending=null;return;}
        var current=mc.player.getMainHandItem();
        if(current!=held.stack) {
            // The server may replace an old SEMI/9mm carrier exactly once with
            // the canonical core schema while this first draw is still locked.
            // A same-ID replacement with any other NBT/count is a real switch.
            if(held.migration==null||!ItemStack.matches(held.migration,current)){pending=null;return;}
            held=new Pending(held.level,held.player,current,null,held.slot,held.count,held.gun,held.display,held.until);pending=held;
        }
        var gun=IGun.getIGunOrNull(held.stack);
        if(gun==null||held.stack.getCount()!=held.count||!held.gun.equals(gun.getGunId(held.stack))
            ||!held.display.equals(display(held.stack))) {pending=null;return;}
        if(!ready(mc,held.stack))return;
        pending=null;
        IClientPlayerGunOperator.fromLocalPlayer(mc.player).inspect();
    }
}
