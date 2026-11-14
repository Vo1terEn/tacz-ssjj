package cn.ssjjzeus;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import java.util.Set;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Check current magazine capacity on both logical sides, including creative mode. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal")
public final class ZeusReloadProtection {
    public static final ResourceLocation GUN=new ResourceLocation("ssjj_zeus","hand_of_zeuss2");
    public static final Set<ResourceLocation> GUNS=Set.of(
        new ResourceLocation("ssjj_zeus","hand_of_zeuss"),
        new ResourceLocation("ssjj_zeus","hand_of_zeuss1"),GUN);
    private ZeusReloadProtection() {}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void reload(GunReloadEvent event) {
        var stack=event.getGunItemStack();var gun=IGun.getIGunOrNull(stack);
        if(gun==null)return;
        ResourceLocation id=gun.getGunId(stack);if(id==null || !GUNS.contains(id))return;
        TimelessAPI.getCommonGunIndex(id).ifPresent(index->apply(event,id,gun.getCurrentAmmoCount(stack),
            AttachmentDataUtils.getAmmoCountWithAttachment(stack,index.getGunData())));
    }
    public static void apply(GunReloadEvent event,ResourceLocation gun,int ammo,int capacity) {
        if(gun!=null && GUNS.contains(gun) && capacity>0 && ammo>=capacity)event.setCanceled(true);
    }
}
