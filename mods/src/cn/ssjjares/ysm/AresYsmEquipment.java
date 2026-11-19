package cn.ssjjares.ysm;

import cn.ssjjares.AresArmorItems;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server owns selection; YSM owns model delivery, first-person rendering and tracking packets. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class AresYsmEquipment {
    private AresYsmEquipment() {}

    static void update(ServerPlayer player, boolean exit) {
        var bridge = AresYsmBridge.binding();
        if (bridge == null) return;
        var persistent = player.getPersistentData();
        boolean enabled = AresAppearanceConfig.enabled();
        if (!enabled && !persistent.contains(AresYsmSelection.BACKUP_KEY, Tag.TAG_COMPOUND)) return;
        try {
            if (!bridge.available()) return;
            var state = bridge.player(player);
            if (state == null) return;
            int tier = enabled && !exit ? AresArmorItems.setTier(player) : 0;
            boolean ready = tier > 0 && bridge.remotePresent(player)
                && bridge.registered(AresYsmSelection.modelForTier(tier));
            AresYsmSelection.reconcile(tier, persistent, state, ready, enabled);
        } catch (ReflectiveOperationException | LinkageError failure) {
            AresYsmBridge.warn("Appearance change deferred; original selection backup retained", failure);
        }
    }

    @SubscribeEvent
    public static void equipment(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSlot().getType() == EquipmentSlot.Type.ARMOR)
            update(player, false);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 10 == 0)
            update(player, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) update(player, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) update(player, false);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) update(player, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void clone(PlayerEvent.Clone event) {
        String key = AresYsmSelection.BACKUP_KEY;
        var previous = event.getOriginal().getPersistentData();
        if (previous.contains(key, Tag.TAG_COMPOUND))
            event.getEntity().getPersistentData().put(key, previous.getCompound(key).copy());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) update(player, true);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (var player : event.getServer().getPlayerList().getPlayers()) update(player, true);
    }
}
