package ssjj.dark;

import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native pistol assets, independent server-owned blade damage, and no parallel bullet path. */
@Mod.EventBusSubscriber(modid=DarkCrystalMod.ID)
public final class DarkGunEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void fire(GunFireEvent event) {
        if (!DarkCombat.weaponId(event.getGunItemStack()).isEmpty()) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void shoot(GunShootEvent event) {
        if (!DarkCombat.weaponId(event.getGunItemStack()).isEmpty()) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void reload(GunReloadEvent event) {
        if (!DarkCombat.weaponId(event.getGunItemStack()).isEmpty()) event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void melee(GunMeleeEvent event) {
        if (!DarkCombat.weaponId(event.getGunItemStack()).isEmpty()) event.setCanceled(true);
    }
    private DarkGunEvents() {}
}
