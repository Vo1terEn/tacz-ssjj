package cn.punishmenteye;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import net.minecraft.world.item.ItemStack;

/** The fitted first-person projection must not be baked into the native refit camera. */
public final class EyeRefitPreview {
    private static final String[] PROJECTION = {
        "eye_video_projection_in", "eye_video_projection_scale", "eye_video_projection_out"
    };
    private EyeRefitPreview() {}

    /** Runs after the native animation update and before native refit positioning. */
    public static void beforeFirstPerson(BedrockAnimatedModel model, ItemStack stack) {
        var gun = IGun.getIGunOrNull(stack);
        if (gun == null) return;
        var id = gun.getGunId(stack);
        if (id == null || !id.getNamespace().equals("eyeofpunishment")) return;
        if (!id.getPath().equals("notdistinguish") && !id.getPath().equals("notdistinguish_base")
                && !id.getPath().equals("notdistinguish_tier1")) return;
        applyOpening(model, RefitTransform.getOpeningProgress());
    }

    /** Remove the fitted projection along the composite rotation's shortest arc. */
    public static void applyOpening(BedrockAnimatedModel model, float opening) {
        if (model == null || !Float.isFinite(opening) || opening <= 0) return;
        float progress = Math.min(opening, 1), keep = 1 - progress;
        var in = model.getNode(PROJECTION[0]);
        var stretch = model.getNode(PROJECTION[1]);
        var out = model.getNode(PROJECTION[2]);
        if (in == null || stretch == null || out == null) return;
        // The fitted chain is R_in * S * R_out, with identity rest rotations.
        // Both R rotations are almost 180 degrees, but their product is small.
        // Independently unwinding them can turn their cancelling pair into a full
        // revolution. Hold the stretch basis R_in fixed and unwind R_in*R_out.
        var rotation = new org.joml.Quaternionf(in.additionalQuaternion)
            .mul(out.additionalQuaternion).normalize();
        rotation.slerp(new org.joml.Quaternionf(), progress);
        var coupledOut = new org.joml.Quaternionf(in.additionalQuaternion)
            .invert().mul(rotation).normalize();
        for (String name : PROJECTION) {
            var node = model.getNode(name);
            node.offsetX *= keep;
            node.offsetY *= keep;
            node.offsetZ *= keep;
            node.xScale = 1 + (node.xScale - 1) * keep;
            node.yScale = 1 + (node.yScale - 1) * keep;
            node.zScale = 1 + (node.zScale - 1) * keep;
            if (progress == 1) node.additionalQuaternion.identity();
        }
        if (progress < 1) out.additionalQuaternion.set(coupledOut);
    }
}
