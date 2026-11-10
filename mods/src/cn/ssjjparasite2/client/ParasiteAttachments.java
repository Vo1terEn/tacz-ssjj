package cn.ssjjparasite2.client;

import cn.fusionarsenal.config.ArsenalConfig;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Exclusive parts use native TaCZ slots; skin scopes remain independent. */
public final class ParasiteAttachments {
    public static final ResourceLocation MUZZLE = new ResourceLocation("ssjjparasite2", "parasite_muzzle");
    public static final ResourceLocation STOCK = new ResourceLocation("ssjjparasite2", "destruction_stock");
    public static final ResourceLocation MAGAZINE = new ResourceLocation("ssjjparasite2", "shockwave_magazine");
    private ParasiteAttachments() {}

    private static boolean has(ItemStack stack, AttachmentType slot, ResourceLocation id) {
        if (stack == null || stack.isEmpty()) return false;
        var gun = IGun.getIGunOrNull(stack);
        return gun != null && ParasiteScopes.family(gun.getGunId(stack)) && id.equals(gun.getAttachmentId(stack, slot));
    }
    public static boolean enhancedDamage(ItemStack stack) { return has(stack, AttachmentType.MUZZLE, MUZZLE); }
    public static boolean retainsDestruction(ItemStack stack) { return has(stack, AttachmentType.STOCK, STOCK); }
    public static double waveRadius(ItemStack stack) {
        return ArsenalConfig.number(has(stack, AttachmentType.EXTENDED_MAG, MAGAZINE)
            ? "parasite.magazine_shockwave_radius" : "parasite.shockwave_radius");
    }
}
