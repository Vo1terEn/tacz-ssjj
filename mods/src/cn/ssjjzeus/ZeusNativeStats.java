package cn.ssjjzeus;

import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import net.minecraft.world.item.ItemStack;

/** Compatibility for the existing native cache hook; gun properties remain native. */
public final class ZeusNativeStats {
    private ZeusNativeStats() {}
    public static void cache(AttachmentCacheProperty cache,ItemStack stack) {}
}
