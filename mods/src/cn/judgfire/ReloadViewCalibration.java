package cn.judgfire;

import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Supplies this gun's model projection before TaCZ's normal FOV dynamics. */
@Mod.EventBusSubscriber(
        modid = "eye_judgment_arsenal",
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ReloadViewCalibration {
    private static final ResourceLocation GUN_ID =
            new ResourceLocation("judgfire", "judgment_flame");
    private static final double MODEL_FOV = 49.35806061D;

    private ReloadViewCalibration() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void calibrateModelView(ViewportEvent.ComputeFov event) {
        // The configured/world FOV path must retain the user's setting.
        if (event.usedConfiguredFov()) {
            return;
        }
        // SRG members deliberately target the supplied Minecraft 1.20.1 runtime.
        LocalPlayer player = Minecraft.m_91087_().f_91074_;
        if (player == null) {
            return;
        }
        ItemStack held = player.m_21205_();
        IGun gun = IGun.getIGunOrNull(held);
        if (gun != null && GUN_ID.equals(gun.getGunId(held))) {
            // TaCZ handles its own smooth hip/ADS interpolation afterwards.
            event.setFOV(MODEL_FOV);
        }
    }
}
