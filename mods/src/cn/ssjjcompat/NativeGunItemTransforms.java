package cn.ssjjcompat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.pojo.TransformScale;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.world.item.ItemDisplayContext;

/** Preserve the installed TaCZ item transform across its public-helper extraction. */
public final class NativeGunItemTransforms {
    private static final Method[] METHODS = resolve();
    private NativeGunItemTransforms() {}

    private static Method checked(Method method) {
        if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class)
            throw new IllegalStateException("Unexpected TaCZ item transform signature: " + method);
        return method;
    }

    private static Method[] resolve() {
        ClassLoader loader = NativeGunItemTransforms.class.getClassLoader();
        ReflectiveOperationException missingPublic;
        try {
            Class<?> modern = Class.forName("com.tacz.guns.client.renderer.item.GunItemTransforms", false, loader);
            Method apply = checked(modern.getMethod("apply", ItemDisplayContext.class,
                TransformScale.class, BedrockGunModel.class, PoseStack.class));
            LogUtils.getLogger().info("[SSJJRPG] Native item transforms: public TaCZ helper");
            return new Method[] { apply };
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            missingPublic = absent;
        }
        try {
            Class<?> legacy = Class.forName("com.tacz.guns.client.renderer.item.GunItemRendererWrapper", false, loader);
            Method position = checked(legacy.getDeclaredMethod("applyPositioningTransform", ItemDisplayContext.class,
                TransformScale.class, BedrockGunModel.class, PoseStack.class));
            Method scale = checked(legacy.getDeclaredMethod("applyScaleTransform", ItemDisplayContext.class,
                TransformScale.class, PoseStack.class));
            position.setAccessible(true);
            scale.setAccessible(true);
            LogUtils.getLogger().info("[SSJJRPG] Native item transforms: legacy TaCZ wrapper methods");
            return new Method[] { position, scale };
        } catch (ReflectiveOperationException | RuntimeException failure) {
            failure.addSuppressed(missingPublic);
            throw new ExceptionInInitializerError(new IllegalStateException(
                "TaCZ exposes neither the public item-transform helper nor accessible legacy wrapper transforms", failure));
        }
    }

    public static String implementation() {
        return METHODS.length == 1 ? "MODERN_PUBLIC" : "LEGACY_WRAPPER";
    }

    /** Resolve once; an exception from a real native invocation is never a fallback signal. */
    public static void apply(ItemDisplayContext context, TransformScale scale, BedrockGunModel model, PoseStack pose) {
        try {
            if (METHODS.length == 1) {
                METHODS[0].invoke(null, context, scale, model, pose);
            } else {
                // These two operations precede both methods in the legacy native renderer.
                pose.translate(0.5, 2.0, 0.5);
                pose.scale(-1.0f, -1.0f, 1.0f);
                METHODS[0].invoke(null, context, scale, model, pose);
                METHODS[1].invoke(null, context, scale, pose);
            }
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Native TaCZ item transform failed via " + implementation(), cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot invoke cached native TaCZ item transform via " + implementation(), failure);
        }
    }
}
