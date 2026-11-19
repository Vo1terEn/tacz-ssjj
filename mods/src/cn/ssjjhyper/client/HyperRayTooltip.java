package cn.ssjjhyper.client;
import cn.ssjjhyper.HyperRays;
import com.tacz.guns.api.item.IGun;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",value=Dist.CLIENT)
public final class HyperRayTooltip {
    private HyperRayTooltip(){}
    @SubscribeEvent public static void tooltip(ItemTooltipEvent event){ /* Skill descriptions intentionally omitted from weapon tooltips. */ }
}
