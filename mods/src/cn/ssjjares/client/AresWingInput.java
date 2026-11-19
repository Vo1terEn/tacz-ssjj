package cn.ssjjares.client;

import cn.ssjjares.AresWingFlight;
import java.lang.ref.WeakReference;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Starts the vanilla gliding packet from a jump edge when Caelus is absent. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", value = Dist.CLIENT)
public final class AresWingInput {
    private static WeakReference<LocalPlayer> lastPlayer = new WeakReference<>(null);
    private static boolean jumpHeld;

    private AresWingInput() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void movement(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || AresWingFlight.usesCaelus()) return;
        if (lastPlayer.get() != player) {
            lastPlayer = new WeakReference<>(player);
            jumpHeld = false;
        }
        boolean pressed = event.getInput().jumping && !jumpHeld;
        jumpHeld = event.getInput().jumping;
        if (!pressed || player.onGround() || player.isSpectator() || player.getAbilities().flying
            || player.isPassenger() || player.onClimbable() || player.isFallFlying()) return;
        var chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (AresWingFlight.flightStack(chest, player) == chest || !player.tryToStartFallFlying()) return;
        player.connection.send(new ServerboundPlayerCommandPacket(player,
            ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
    }
}
