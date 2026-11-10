package cn.ssjjparasite2.client;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
/** Same ordinary 64-stack crafting material policy as the other two families. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ParasiteFragment {
 public static final ResourceLocation ID=new ResourceLocation("eye_judgment_arsenal","parasite_fragment");
 @SubscribeEvent public static void register(RegisterEvent event){event.register(Registries.ITEM,helper->helper.register(ID,new Item(new Item.Properties().stacksTo(64))));}
 @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey().equals(CreativeModeTabs.INGREDIENTS))event.accept(BuiltInRegistries.ITEM.get(ID));}
}
