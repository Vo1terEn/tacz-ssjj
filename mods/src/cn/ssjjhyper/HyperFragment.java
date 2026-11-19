package cn.ssjjhyper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Shares the established material registry and ingredients creative tab. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class HyperFragment {
    public static final ResourceLocation ID=new ResourceLocation("eye_judgment_arsenal","hyper_revolver_fragment");
    private HyperFragment() {}
    @SubscribeEvent public static void registerHyperFragment(RegisterEvent event) {
        event.register(Registries.ITEM,helper->helper.register(ID,new Item(new Item.Properties().stacksTo(64))));
    }
    @SubscribeEvent public static void creativeHyperFragment(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey().equals(CreativeModeTabs.INGREDIENTS))event.accept(BuiltInRegistries.ITEM.get(ID));
    }
}
