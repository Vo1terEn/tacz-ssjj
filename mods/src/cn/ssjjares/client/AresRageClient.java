package cn.ssjjares.client;

import cn.ssjjares.AresAccessoryConfig.Stat;
import cn.ssjjares.AresAccessories;
import cn.ssjjares.AresArmorItems;
import cn.ssjjares.AresCombat;
import cn.ssjjares.AresRageNetwork;
import cn.ssjjcompat.ArsenalKeyBindings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", value = Dist.CLIENT)
public final class AresRageClient {
    private static final IKeyConflictContext CONTEXT = new IKeyConflictContext() {
        @Override public boolean isActive() {
            var client = Minecraft.getInstance();
            return client != null && client.player != null && client.level != null
                && client.screen == null && client.getOverlay() == null && !client.isPaused()
                && client.isWindowActive() && client.mouseHandler.isMouseGrabbed()
                && client.player.isAlive() && !client.player.isSpectator()
                && AresArmorItems.setTier(client.player) != 0;
        }

        @Override public boolean conflicts(IKeyConflictContext other) {
            return other == this || other == KeyConflictContext.IN_GAME;
        }
    };
    private static final KeyMapping KEY = ArsenalKeyBindings.create(
        "key.ssjj_ares.war_rage", CONTEXT, InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
    private AresRageClient() {}

    @Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            ArsenalKeyBindings.register(event, KEY);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS
            || !KEY.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(event.getButton()))) return;
        event.setCanceled(true);
        AresRageNetwork.request();
    }

    @SubscribeEvent
    public static void key(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS
            && KEY.isActiveAndMatches(InputConstants.getKey(event.getKey(), event.getScanCode())))
            AresRageNetwork.request();
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!AresArmorItems.isArmorId(id)) return;
        int tier = id.getPath().endsWith("_absolute") ? 2 : 1;
        int kind = AresAccessories.kind(stack);
        var lines = event.getToolTip();
        lines.add(Component.translatable("ssjj_ares.tooltip.set", AresCombat.setDamagePercent(tier)).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("ssjj_ares.tooltip.rage", KEY.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));
        if (kind == 0) return;
        lines.add(Component.translatable("ssjj_ares.tooltip.accessory", Stat.DAMAGE.integer(kind, tier)).withStyle(ChatFormatting.GOLD));
        switch (kind) {
            case 1 -> lines.add(Component.translatable("ssjj_ares.tooltip.mask",
                Stat.RAGE_COOLDOWN.integer(kind, tier)).withStyle(ChatFormatting.GRAY));
            case 2 -> lines.add(Component.translatable("ssjj_ares.tooltip.cannons", Stat.MISSILE_INTERVAL.integer(kind, tier),
                format(Stat.MISSILE_BASE.number(kind, tier)), format(Stat.MISSILE_HEALTH.number(kind, tier))).withStyle(ChatFormatting.GRAY));
            case 3 -> {
                lines.add(Component.translatable("ssjj_ares.tooltip.wings", format(Stat.REDUCTION.number(kind, tier)))
                    .withStyle(ChatFormatting.GRAY));
                if (Stat.FLIGHT.enabled(kind, tier))
                    lines.add(Component.translatable("ssjj_ares.tooltip.flight").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static String format(double amount) {
        return amount == Math.rint(amount) ? Long.toString((long) amount) : Double.toString(amount);
    }
}
