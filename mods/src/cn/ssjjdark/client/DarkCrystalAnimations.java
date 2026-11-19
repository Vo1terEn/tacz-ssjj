package cn.ssjjdark.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.AnimationController;
import com.tacz.guns.api.client.animation.statemachine.AnimationStateMachine;
import com.tacz.guns.api.item.IGun;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only an acknowledged server attack starts a blade action and its original sound. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DarkCrystalAnimations {
    private static ClientLevel level;
    private static Object player;
    private static ItemStack stack;
    private static int slot=-1, remaining;
    private static ResourceLocation gunId;
    private static boolean heavy;
    private static AnimationStateMachine<?> active;
    private static AnimationController controller;
    private static int actionTrack=-1;
    private static ResourceLocation lastLight;
    private static boolean alternate;
    private DarkCrystalAnimations() {}
    public static boolean family(ResourceLocation id) {
        return id!=null && id.getNamespace().equals("ssjj_dark")
            && (id.getPath().equals("darkdiamond") || id.getPath().equals("darkdiamond1") || id.getPath().equals("darkdiamond2"));
    }
    private static void world() {
        var next=Minecraft.getInstance().level;
        if(next!=level) {clear();lastLight=null;alternate=false;level=next;}
    }
    public static void play(UUID owner,int selected,ResourceLocation gun,boolean right) {
        world();var mc=Minecraft.getInstance();var p=mc.player;
        if(level==null || p==null || !p.isAlive() || p.isSpectator() || !p.getUUID().equals(owner)
            || selected!=p.getInventory().selected || !family(gun))return;
        var held=p.getMainHandItem();var item=IGun.getIGunOrNull(held);
        if(item==null || !gun.equals(item.getGunId(held)))return;
        clear();player=p;stack=held;slot=selected;gunId=gun;heavy=right;remaining=10;
        start();
    }
    private static boolean held() {
        var p=Minecraft.getInstance().player;
        if(p==null || p!=player || !p.isAlive() || p.isSpectator() || p.getInventory().selected!=slot || p.getMainHandItem()!=stack)return false;
        var item=IGun.getIGunOrNull(stack);return item!=null && gunId.equals(item.getGunId(stack));
    }
    private static void start() {
        if(!held()){clear();return;}
        var display=TimelessAPI.getGunDisplay(stack).orElse(null);
        if(display==null)return;
        var machine=display.getAnimationStateMachine();
        if(machine==null || !machine.isInitialized() || machine.getContext()==null)return;
        var context=machine.getContext();
        String clip="dark_heavy";
        if(!heavy) {
            if(!gunId.equals(lastLight)){alternate=false;lastLight=gunId;}
            clip=alternate?"dark_light_alt":"dark_light";
        }
        if(!context.hasAnimationPrototype(clip))return;
        context.ensureTracksAmount(0,2);
        active=machine;controller=machine.getAnimationController();actionTrack=context.getTrack(0,1);
        machine.trigger(clip);
        if(!heavy)alternate=!alternate;
        var p=Minecraft.getInstance().player;
        var sound=new ResourceLocation("ssjj_dark",gunId.getPath()+(heavy?"_sec_fire":"_fire"));
        level.playLocalSound(p.getX(),p.getY(),p.getZ(),SoundEvent.createVariableRangeEvent(sound),SoundSource.PLAYERS,1,1,false);
        remaining=0;
    }
    private static void clear() {
        if(controller!=null && actionTrack>=0) {
            var runner=controller.getAnimation(actionTrack);
            if(runner!=null && runner.getAnimation().name.startsWith("dark_"))controller.removeAnimation(actionTrack);
        }
        player=null;stack=null;slot=-1;gunId=null;remaining=0;active=null;controller=null;actionTrack=-1;
    }
    @SubscribeEvent public static void tickDarkCrystalAnimations(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;world();var mc=Minecraft.getInstance();
        if(stack!=null) {
            if(!held()){clear();return;}
            if(active!=null && (!active.isInitialized() || active.getAnimationController()!=controller)){clear();return;}
            if(remaining>0){--remaining;start();if(remaining==0 && active==null)clear();}
        }
        if(mc.player==null || level==null || mc.isPaused())return;
        if(DarkLiberationPresentation.normalViewActive())return;
        var held=mc.player.getMainHandItem();var item=IGun.getIGunOrNull(held);
        if(item==null || !family(item.getGunId(held)))return;
        TimelessAPI.getGunDisplay(held).ifPresent(display->{
            var machine=display.getAnimationStateMachine();
            if(machine==null || !machine.isInitialized() || machine.getContext()==null)return;
            var velocity=mc.player.getDeltaMovement();
            boolean moving=velocity.horizontalDistanceSqr()>1.0e-5;
            machine.trigger(moving?"dark_walk":"dark_idle");
        });
    }
}
