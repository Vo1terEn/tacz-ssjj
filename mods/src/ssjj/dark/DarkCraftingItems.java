package ssjj.dark;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

/** 黯水晶普通合成材料，沿用现有武器碎片的材料栏及64堆叠规则。 */
@Mod.EventBusSubscriber(modid = "ssjj_dark", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DarkCraftingItems {
    public static final ResourceLocation FRAGMENT_ID = new ResourceLocation("ssjj_dark", "darkcrystal_fragment");
    private static final ResourceLocation INGREDIENTS_TAB = new ResourceLocation("minecraft", "ingredients");

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ITEMS, FRAGMENT_ID,
            () -> new Item(new Item.Properties().m_41487_(64)));
    }

    @SubscribeEvent
    public static void creative(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().m_135782_().equals(INGREDIENTS_TAB)) return;
        Item item = ForgeRegistries.ITEMS.getValue(FRAGMENT_ID);
        if (item != null)
            event.m_246267_(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
    }

    private DarkCraftingItems() {}
}
