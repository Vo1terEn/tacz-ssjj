package cn.ssjjparasite2.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Passive energy is derived from the current, legally installed dedicated parts. */
public final class ParasiteAccessoryEnergy {
    private static final ResourceLocation STOCK = new ResourceLocation("ssjjparasite2", "destruction_stock");
    private static final ResourceLocation MAGAZINE = new ResourceLocation("ssjjparasite2", "shockwave_magazine");
    private ParasiteAccessoryEnergy() {}

    private static boolean installed(ItemStack stack, IGun gun, ResourceLocation gunId,
                                     AttachmentType slot, ResourceLocation attachment) {
        return gun.allowAttachmentType(stack, slot)
            && attachment.equals(gun.getAttachmentId(stack, slot))
            && AllowAttachmentTagMatcher.match(gunId, attachment);
    }

    private static int count(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var gun = IGun.getIGunOrNull(stack);
        if (gun == null) return 0;
        var gunId = gun.getGunId(stack);
        if (!ParasiteScopes.family(gunId)) return 0;
        return (installed(stack, gun, gunId, AttachmentType.STOCK, STOCK) ? 1 : 0)
            + (installed(stack, gun, gunId, AttachmentType.EXTENDED_MAG, MAGAZINE) ? 1 : 0);
    }

    public static double strength(ItemStack stack) { return count(stack) * 800d; }
    public static double resistanceOrPenetration(ItemStack stack, boolean penetration) {
        return count(stack) * (penetration ? 750d : 250d);
    }
}
