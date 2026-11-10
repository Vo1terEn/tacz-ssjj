package cn.judgfire;

import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Convert the native FOV-compensated hand muzzle once, without sharing mutable vectors. */
public final class BeamOrigin {
  public static Vec3 snapshot(Vec3 camera, Vector3f nativeOffset, Quaternionf cameraRotation) {
    // Camera.rotation maps +Z forward / +X left; the GL gun pose uses -Z
    // forward / +X right. Invert those two axes before camera-to-world rotation.
    Vector3f v =
        new Vector3f(-nativeOffset.x, nativeOffset.y, -nativeOffset.z).rotate(cameraRotation);
    if (!Float.isFinite(v.x) || !Float.isFinite(v.y) || !Float.isFinite(v.z)) return camera;
    return camera.add(v.x, v.y, v.z);
  }
}
