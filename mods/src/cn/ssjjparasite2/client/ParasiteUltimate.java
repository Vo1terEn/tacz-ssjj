package cn.ssjjparasite2.client;

import cn.fusionarsenal.api.UltimateStoneRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Uses the installed arsenal registry, including its native LASER-slot and whitelist checks. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ParasiteUltimate {
    public static final ResourceLocation STONE = new ResourceLocation("ssjjparasite2", "ultimate_upgrade_stone");
    public static final float BASE_RETENTION_CHANCE = .30f;
    // The stock remains required; either legal ultimate stone raises its chance to 70%.
    public static final float ULTIMATE_RETENTION_CHANCE = .70f;
    static { UltimateStoneRegistry.register(ParasiteScopes.GUN, STONE); }
    private ParasiteUltimate() {}

    @SubscribeEvent public static void registerParasiteUltimate(FMLCommonSetupEvent event) {
        UltimateStoneRegistry.register(ParasiteScopes.GUN, STONE);
    }
    public static boolean installed(ItemStack stack) {
        return UltimateStoneRegistry.installed(stack, STONE);
    }
    public static float retentionChance(ItemStack stack) {
        if (!ParasiteAttachments.retainsDestruction(stack)) return 0;
        return installed(stack) ? ULTIMATE_RETENTION_CHANCE : BASE_RETENTION_CHANCE;
    }
}
