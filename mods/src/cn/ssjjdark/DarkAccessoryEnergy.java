package cn.ssjjdark;

import cn.fusionarsenal.api.UltimateStoneRegistry;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.world.item.ItemStack;

/** Adds the same flat amount to each energy attribute for every eligible installed accessory. */
public final class DarkAccessoryEnergy {
    private static final AttachmentType[] SLOTS = AttachmentType.values();
    private static final int ENERGY_PER_PIECE = 300;

    private DarkAccessoryEnergy() {}

    public static double bonus(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        var gun = IGun.getIGunOrNull(stack);
        if (gun == null) return 0;
        var gunId = gun.getGunId(stack);
        if (gunId == null || !DarkDamageRules.gun(gunId.getNamespace(), gunId.getPath())) return 0;

        var nativeStone = UltimateStoneRegistry.nativeStone(gunId);
        int count = 0;
        for (var slot : SLOTS) {
            if (slot == AttachmentType.NONE || slot == AttachmentType.SCOPE || !gun.allowAttachmentType(stack, slot)) continue;
            var id = gun.getAttachmentId(stack, slot);
            if (id == null || id.equals(DarkUltimate.ID) || id.equals(UltimateStoneRegistry.CREATIVE)
                || id.equals(nativeStone)) continue;
            if (TimelessAPI.getCommonAttachmentIndex(id).filter(index -> index.getType() == slot).isPresent()
                && AllowAttachmentTagMatcher.match(gunId, id)) count++;
        }
        return count * ENERGY_PER_PIECE;
    }
}
