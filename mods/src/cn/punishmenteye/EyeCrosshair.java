package cn.punishmenteye;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(EyeCrosshair.MOD_ID)
public final class EyeCrosshair {
    public static final String MOD_ID = "eyeofpunishment_crosshair";
    public EyeCrosshair() {
        SkillTimer.init(); PassivePower.init(); NofaNetwork.init();
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.COMMON,PassivePower.CONFIG,"eyeofpunishment-common.toml");
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class Client {
        private static final ResourceLocation GUN = new ResourceLocation("eyeofpunishment", "notdistinguish");
        private static final ResourceLocation TEXTURE = new ResourceLocation("eyeofpunishment", "textures/gui/crosshair.png");

        static boolean holdsEye(Minecraft mc) {
            if (mc.player == null || mc.level == null || mc.player.isSpectator()) return false;
            var stack = mc.player.getMainHandItem();
            return stack.getItem() instanceof IGun gun && GUN.equals(gun.getGunId(stack));
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void clearPreviousAim(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
            var mc=Minecraft.getInstance();
            if(!holdsEye(mc))return;
            var operator=com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator.fromLocalPlayer(mc.player);
            if(operator.isAim())operator.aim(false);
            var data=operator.getDataHolder();
            data.clientAimingProgress=0;
            com.tacz.guns.client.gameplay.LocalPlayerDataHolder.oldAimingProgress=0;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public static void replace(RenderGuiOverlayEvent.Pre event) {
            if (VanillaGuiOverlay.CROSSHAIR.id().equals(event.getOverlay().id()) && holdsEye(Minecraft.getInstance())) {
                // Prevent both vanilla and TaCZ's normal crosshair for this main-hand ID.
                event.setCanceled(true);
            }
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void draw(RenderGuiEvent.Post event) {
            var mc = Minecraft.getInstance();
            if (!holdsEye(mc) || mc.options.hideGui || mc.screen != null || !mc.options.getCameraType().isFirstPerson()) return;
            var gui = event.getGuiGraphics();
            int x = event.getWindow().getGuiScaledWidth() / 2 - 16;
            int y = event.getWindow().getGuiScaledHeight() / 2 - 16;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            gui.blit(TEXTURE, x, y, 32, 32, 0, 0, 104, 104, 104, 104);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
        }
    }
}
