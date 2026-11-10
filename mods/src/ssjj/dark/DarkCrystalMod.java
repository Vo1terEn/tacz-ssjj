package ssjj.dark;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/** 黯水晶独立近战物品入口，数据和外观由近战资源包提供。 */
@Mod(DarkCrystalMod.ID)
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
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, DarkSettings.SPEC);
        DarkNetwork.register();
        DarkCombat.register();
    }
}
