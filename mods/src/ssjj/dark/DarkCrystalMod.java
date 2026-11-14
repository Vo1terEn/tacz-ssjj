package ssjj.dark;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** Internal module lifecycle. The stable ssjj_dark ID names saved items and resources. */
public final class DarkCrystalMod {
    public static final String ID = "ssjj_dark";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);

    static {
        ITEMS.register("melee", DarkMeleeItem::new);
    }

    public DarkCrystalMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        net.minecraftforge.fml.DistExecutor.safeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> DarkClientBootstrap.register(bus));
        // Explicit old name preserves existing world/server configuration after container fusion.
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DarkSettings.SPEC, "ssjj_dark-server.toml");
        DarkNetwork.register();
        DarkCombat.register();
    }
}
