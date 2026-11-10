package cn.ssjjcompat;

import com.tacz.guns.api.item.IGun;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Adjust only the description bound; TaCZ and GunsmithLib retain the call itself. */
public final class DescriptionLines {
    private DescriptionLines() {}

    public static int upperBound(List<?> lines, int from, int to, ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        ResourceLocation id = gun == null ? null : gun.getGunId(stack);
        return upperBoundForId(lines, from, to, id == null ? null : id.toString());
    }

    public static int upperBoundForId(List<?> lines, int from, int to, String gunId) {
        if ("ssjjparasite2:parasitic_sniper".equals(gunId)
                || "ssjjparasite2:parasitic_sniper1".equals(gunId)
                || "ssjjparasite2:parasitic_sniper2".equals(gunId)) {
            return lines.size();
        }
        return to;
    }
}
