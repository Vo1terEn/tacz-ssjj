package cn.punishmenteye;

import com.tacz.guns.api.item.IGun;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Checked before TaCZ changes aiming state or sends an aim packet. */
public final class AimRules {
    private static final ResourceLocation GUN=new ResourceLocation("eyeofpunishment","notdistinguish");
    public static boolean blocksId(ResourceLocation id,boolean requested) {
        return requested&&GUN.equals(id);
    }
    public static boolean blocksAim(LivingEntity entity,boolean requested) {
        if(!requested||entity==null)return false;
        var stack=entity.getMainHandItem();
        return stack.getItem() instanceof IGun gun&&blocksId(gun.getGunId(stack),true);
    }
}
