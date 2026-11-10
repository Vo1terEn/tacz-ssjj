package cn.eyejudgment;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Ordinary crafting materials; these items are never ammunition or attachments. */
@Mod.EventBusSubscriber(modid = FragmentItems.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FragmentItems {
    public static final String MOD_ID = "eye_judgment_arsenal";
    public static final String JUDGMENT_FLAME_FRAGMENT = "judgment_flame_fragment";
    public static final String PUNISHMENT_EYE_FRAGMENT = "punishment_eye_fragment";

    private FragmentItems() {}

    @SubscribeEvent
    public static void registerItems(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> {
            helper.register(id(JUDGMENT_FLAME_FRAGMENT), new Item(new Item.Properties().stacksTo(64)));
            helper.register(id(PUNISHMENT_EYE_FRAGMENT), new Item(new Item.Properties().stacksTo(64)));
        });
    }

    @SubscribeEvent
    public static void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.INGREDIENTS)) {
            event.accept(BuiltInRegistries.ITEM.get(id(JUDGMENT_FLAME_FRAGMENT)));
            event.accept(BuiltInRegistries.ITEM.get(id(PUNISHMENT_EYE_FRAGMENT)));
        }
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
