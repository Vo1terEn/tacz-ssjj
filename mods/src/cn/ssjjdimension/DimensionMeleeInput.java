package cn.ssjjdimension;

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

/** A scoped RMB replacement. Native TaCZ owns cooldown, network and damage. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal", value=Dist.CLIENT)
public final class DimensionMeleeInput {
    private DimensionMeleeInput() {}

    public static boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof IGun)) return false;
        var tag=stack.getTag();
        return tag!=null && tag.contains("GunId",8)
            && DimensionScopes.family(net.minecraft.resources.ResourceLocation.tryParse(tag.getString("GunId")));
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
        // Clear before TaCZ polls HOLD_TO_AIM, and again after its handlers.
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
        // Cancel Pre so vanilla use, TaCZ ADS, rebound shoot/zoom/melee Post
        // handlers cannot turn one physical click into multiple actions.
        event.setCanceled(true);
        suppressAim(mc);
        if (event.getAction()!=GLFW.GLFW_PRESS) return;
        var server=IGunOperator.fromLivingEntity(mc.player);
        var reload=server.getSynReloadState();
        if (reload!=null && reload.getStateType().isReloading()) return;
        if (server.getSynDrawCoolDown()>0 || server.getSynIsBolting()
            || server.getSynMeleeCoolDown()>0) return;
        IClientPlayerGunOperator.fromLocalPlayer(mc.player).melee();
    }
}
