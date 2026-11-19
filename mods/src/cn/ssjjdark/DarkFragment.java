package cn.ssjjdark;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegisterEvent;

/** Uses the shared fragment registry and ordinary ingredients creative tab. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DarkFragment {
    public static final ResourceLocation ID=new ResourceLocation("eye_judgment_arsenal","darkcrystal_fragment");
    private DarkFragment(){}
    @SubscribeEvent public static void registerDarkCrystalFragment(RegisterEvent event){
        event.register(Registries.ITEM,helper->helper.register(ID,new Item(new Item.Properties().stacksTo(64))));
    }
    @SubscribeEvent public static void creativeDarkCrystalFragment(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey().equals(CreativeModeTabs.INGREDIENTS))event.accept(BuiltInRegistries.ITEM.get(ID));
    }
    @SubscribeEvent public static void setupDarkCrystalCombat(FMLCommonSetupEvent event){DarkCombat.init();}
}
