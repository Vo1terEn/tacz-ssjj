package cn.ssjjdimension;

import cn.fusionarsenal.api.UltimateStoneRegistry;
import cn.ssjjparasite2.client.ParasiteUltimate;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Extends the existing legal LASER-slot registry; shared creative stats stay native. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DimensionStoneCompat {
 public static final ResourceLocation GUN=new ResourceLocation("ssjj_dimension","dimension_star2");
 public static final ResourceLocation STONE=new ResourceLocation("ssjj_dimension","ultimate_upgrade_stone");
 static {init();}
 private DimensionStoneCompat(){}
 /** Called by the existing constructor bootstrap, and safely repeated at common setup. */
 public static void init(){
  UltimateStoneRegistry.register(GUN,STONE);
  // Reuse the exact current Parasite policy (creative alias, faster switch, stock prerequisite).
  ParasiteUltimate.registerParasiteUltimate(null);
 }
 @SubscribeEvent public static void setupDimensionStones(FMLCommonSetupEvent event){init();}
}
