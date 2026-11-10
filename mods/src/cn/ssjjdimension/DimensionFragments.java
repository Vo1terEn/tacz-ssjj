package cn.ssjjdimension;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** Common-side item registration, matching the existing weapon fragment items. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DimensionFragments {
    public static final ResourceLocation ID = new ResourceLocation("eye_judgment_arsenal", "dimension_star_fragment");
    private static final ResourceLocation INGREDIENTS = new ResourceLocation("minecraft", "ingredients");

    private DimensionFragments() {}

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ITEMS, ID, () -> new Item(new Item.Properties().stacksTo(64)));
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().location().equals(INGREDIENTS)) return;
        Item fragment = ForgeRegistries.ITEMS.getValue(ID);
        if (fragment != null) {
            event.accept(new ItemStack(fragment), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
