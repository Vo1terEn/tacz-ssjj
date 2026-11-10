package ssjj.dark;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** 相机位置运算独立于渲染事件，防止SRG加减入口误用把后方镜头推到面前。 */
final class DarkCameraGeometry {
    static Vec3 behind(Vec3 eye, Vec3 forward, double distance) {
        return eye.m_82546_(forward.m_82490_(distance));
    }

    static Vec3 towardEye(Vec3 collision, Vec3 forward, double margin) {
        return collision.m_82549_(forward.m_82490_(margin));
    }

    /** 世界相对相机坐标转观察空间；AFTER_LEVEL提供的是投影栈，不能再用作此矩阵。 */
    static Matrix4f worldToView(float yaw, float pitch, Vec3 relative) {
        return new Matrix4f().rotationX((float) Math.toRadians(pitch))
            .rotateY((float) Math.toRadians(yaw + 180))
            .translate((float) relative.f_82479_, (float) relative.f_82480_, (float) relative.f_82481_);
    }

    private DarkCameraGeometry() {}
}
