package cn.punishmenteye;

import com.tacz.guns.api.event.common.GunReloadEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Server authority also rejects redundant reload packets from unmodified clients. */
@Mod.EventBusSubscriber(modid=EyeCrosshair.MOD_ID)
public final class ReloadEvents {
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void reload(GunReloadEvent event) {
        if(event.getLogicalSide()==LogicalSide.SERVER&&ReloadRules.blocksReload(event.getGunItemStack()))
            event.setCanceled(true);
    }
}
