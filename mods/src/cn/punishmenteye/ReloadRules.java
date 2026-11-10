package cn.punishmenteye;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Reject redundant reloads before TaCZ locks the client or starts server reloads. */
public final class ReloadRules {
    private ReloadRules() {}

    public static boolean full(int magazine,int capacity) {
        // Match native canReload: capacity is magazine capacity. For closed bolts,
        // capacity-1 in the magazine plus one chambered round remains reloadable.
        return capacity>0&&magazine>=capacity;
    }

    public static boolean blocksId(ResourceLocation id,int magazine,int capacity) {
        return WeaponIdentity.matches(id)&&full(magazine,capacity);
    }

    public static boolean blocksReload(LivingEntity entity) {
        return entity!=null&&blocksReload(entity.getMainHandItem());
    }

    public static boolean blocksReload(ItemStack stack) {
        if(stack==null||stack.isEmpty()||!(stack.getItem() instanceof IGun gun))return false;
        var id=gun.getGunId(stack);
        if(!WeaponIdentity.matches(id)||gun.useInventoryAmmo(stack))return false;
        var index=TimelessAPI.getCommonGunIndex(id).orElse(null);
        if(index==null||index.getGunData()==null)return false;
        var data=index.getGunData();
        // Use the same attachment-aware capacity as native canReload and filling.
        return full(gun.getCurrentAmmoCount(stack),
                AttachmentDataUtils.getAmmoCountWithAttachment(stack,data));
    }
}
