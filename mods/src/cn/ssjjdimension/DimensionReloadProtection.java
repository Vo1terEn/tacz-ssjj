package cn.ssjjdimension;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Common-side event catches creative and survival paths using current attachment capacity. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class DimensionReloadProtection {
    public static final ResourceLocation GUN=new ResourceLocation("ssjj_dimension","dimension_star2");
    private DimensionReloadProtection() {}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void reload(GunReloadEvent event) {
        var stack=event.getGunItemStack();var gun=IGun.getIGunOrNull(stack);
        if(gun==null || !DimensionScopes.family(gun.getGunId(stack)))return;
        var id=gun.getGunId(stack);
        TimelessAPI.getCommonGunIndex(id).ifPresent(index->apply(event,id,gun.getCurrentAmmoCount(stack),
            AttachmentDataUtils.getAmmoCountWithAttachment(stack,index.getGunData())));
    }
    public static void apply(GunReloadEvent event,ResourceLocation gun,int ammo,int capacity) {
        if(DimensionScopes.family(gun) && capacity>0 && ammo>=capacity)event.setCanceled(true);
    }
}
