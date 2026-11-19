package cn.ssjjdimension.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keep the existing subscriber ABI while omitting the two growth description lines. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class DimensionGrowthTooltip {
    private DimensionGrowthTooltip(){}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){}
}
