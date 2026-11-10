package cn.judgfire;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Only the local player's entity stores its received deadline. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class StunInput {
    private StunInput() {}
    public static void receive(StunNetwork.Status state) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.getId() != state.entityId() || !player.getUUID().equals(state.player()) || !player.level().dimension().location().equals(state.dimension())) return;
        if (state.ticks() == 0) player.getPersistentData().remove(StunState.CLIENT);
        else player.getPersistentData().putLong(StunState.CLIENT, player.level().getGameTime() + (long) state.ticks());
    }
    @SubscribeEvent public static void input(MovementInputUpdateEvent event) {
        if (!BurstStun.stunned(event.getEntity())) return;
        var input = event.getInput();
        input.forwardImpulse = 0;
        input.leftImpulse = 0;
        input.jumping = false;
        var v = event.getEntity().getDeltaMovement();
        event.getEntity().setDeltaMovement(0, Math.min(0, v.y), 0);
    }
}
