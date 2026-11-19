package cn.ssjjhyper.client;

import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.AimKey;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** Routes this family's RMB to native TaCZ melee, including its server checks. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class HyperMeleeInput {
    private HyperMeleeInput() {}

    public static boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IGun gun)) return false;
        var tag=stack.getTag();
        if (tag==null || !tag.contains("GunId",8)) return false;
        var id=gun.getGunId(stack);
        if (id==null || !id.getNamespace().equals("ssjj_hyper")) return false;
        return switch (id.getPath()) {
            case "hyper_revolver", "hyper_revolver1", "hyper_revolver2" -> true;
            default -> false;
        };
    }

    private static boolean held(Minecraft mc) {
        return mc.player!=null && matches(mc.player.getMainHandItem());
    }

    private static boolean enabled(Minecraft mc) {
        return held(mc) && mc.level!=null && mc.screen==null && mc.getOverlay()==null && !mc.isPaused()
            && mc.isWindowActive() && mc.mouseHandler.isMouseGrabbed()
            && mc.player.isAlive() && !mc.player.isSpectator();
    }

    private static void suppressAim(Minecraft mc) {
        if (!held(mc)) return;
        // Both sides of the native HOLD poll also clear ADS after a hotbar switch.
        AimKey.AIM_KEY.setDown(false);
        var operator=IClientPlayerGunOperator.fromLocalPlayer(mc.player);
        if (operator.isAim()) operator.aim(false);
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void beforeTick(TickEvent.ClientTickEvent event) {
        suppressAim(Minecraft.getInstance());
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void afterTick(TickEvent.ClientTickEvent event) {
        suppressAim(Minecraft.getInstance());
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        var mc=Minecraft.getInstance();
        if (!enabled(mc) || event.getButton()!=GLFW.GLFW_MOUSE_BUTTON_RIGHT) return;
        suppressAim(mc);
        // Let vanilla and native Post clear every binding on this button. This
        // also releases a use/shoot key pressed before switching to this gun.
        if (event.getAction()==GLFW.GLFW_RELEASE) return;
        // Consume presses before native use/ADS or a rebound Post action.
        event.setCanceled(true);
        if (event.getAction()!=GLFW.GLFW_PRESS) return;
        var operator=IGunOperator.fromLivingEntity(mc.player);
        var reload=operator.getSynReloadState();
        if (reload!=null && reload.getStateType().isReloading()) return;
        if (operator.getSynDrawCoolDown()>0 || operator.getSynIsBolting()
            || operator.getSynMeleeCoolDown()>0) return;
        // Deliberately no ammo gate. Native state lock, packet, prep, damage and
        // switch cancellation are the same path used by the existing V key.
        IClientPlayerGunOperator.fromLocalPlayer(mc.player).melee();
    }
}
