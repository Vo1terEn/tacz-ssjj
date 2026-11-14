package cn.ssjjzeus;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

/** Uses the same Forge material registration and creative tab as existing fragments. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class ZeusFragment {
 public static final ResourceLocation ID=new ResourceLocation("eye_judgment_arsenal","zeus_hand_fragment");
 @SubscribeEvent public static void registerZeusFragment(RegisterEvent event) {
  event.register(Registries.ITEM,helper->helper.register(ID,new Item(new Item.Properties().stacksTo(64))));
 }
 @SubscribeEvent public static void creativeZeusFragment(BuildCreativeModeTabContentsEvent event) {
  if(event.getTabKey().equals(CreativeModeTabs.INGREDIENTS))event.accept(BuiltInRegistries.ITEM.get(ID));
 }
}
