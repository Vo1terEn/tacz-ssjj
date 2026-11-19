package cn.ssjjhyper;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.AttachmentDataUtils;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Native ammunition and attachment capacities remain authoritative on both sides. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class HyperReloadProtection {
    public static final Set<ResourceLocation> GUNS=Set.of(
        new ResourceLocation("ssjj_hyper","hyper_revolver"),
        new ResourceLocation("ssjj_hyper","hyper_revolver1"),
        new ResourceLocation("ssjj_hyper","hyper_revolver2"));
    private HyperReloadProtection() {}
    public static boolean family(ResourceLocation id) {return id!=null && GUNS.contains(id);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void reload(GunReloadEvent event) {
        var stack=event.getGunItemStack();var gun=IGun.getIGunOrNull(stack);
        if(gun==null || !family(gun.getGunId(stack)))return;
        TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).ifPresent(index->{
            int capacity=AttachmentDataUtils.getAmmoCountWithAttachment(stack,index.getGunData());
            if(capacity>0 && gun.getCurrentAmmoCount(stack)>=capacity)event.setCanceled(true);
        });
    }
}
