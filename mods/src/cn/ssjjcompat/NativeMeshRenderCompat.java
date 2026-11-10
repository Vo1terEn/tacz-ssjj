package cn.ssjjcompat;

import com.tacz.guns.api.item.IGun;
import java.util.Set;
import net.minecraft.world.item.ItemStack;

/** These guns attach source triangles and effects after native model parsing. */
public final class NativeMeshRenderCompat {
    private NativeMeshRenderCompat() {}

    private static final Set<String> GUNS = Set.of(
        "eyeofpunishment:notdistinguish_base", "eyeofpunishment:notdistinguish_tier1", "eyeofpunishment:notdistinguish",
        "judgfire:judgment_flame_base", "judgfire:judgment_flame_tier1", "judgfire:judgment_flame",
        "ssjj_dark:darkdiamond", "ssjj_dark:darkdiamond1", "ssjj_dark:darkdiamond2",
        "ssjj_dimension:dimension_star", "ssjj_dimension:dimension_star1", "ssjj_dimension:dimension_star2",
        "ssjjparasite2:parasitic_sniper", "ssjjparasite2:parasitic_sniper1", "ssjjparasite2:parasitic_sniper2"
    );

    public static boolean preferNative(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        IGun gun = IGun.getIGunOrNull(stack);
        return gun != null && GUNS.contains(gun.getGunId(stack).toString());
    }
}
