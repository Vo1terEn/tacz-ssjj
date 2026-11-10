package cn.ssjjcompat.client;

import org.joml.Quaternionf;

/** Shared 18-channel LUT interpolation; callers retain profile validation and lifetime rules. */
public final class ParticleFrames {
    private ParticleFrames() {}

    public static float[] sample(float[][] frames, float phase) {
        float position = Math.max(0, Math.min(1, phase)) * (frames.length - 1);
        int i = (int) position, j = Math.min(i + 1, frames.length - 1);
        float f = position - i;
        float[] a = frames[i], b = frames[j], out = new float[18];
        for (int k = 0; k < 18; k++) out[k] = a[k] + (b[k] - a[k]) * f;
        Quaternionf q = new Quaternionf(a[6], a[7], a[8], a[9]).normalize()
            .slerp(new Quaternionf(b[6], b[7], b[8], b[9]).normalize(), f).normalize();
        out[6] = q.x; out[7] = q.y; out[8] = q.z; out[9] = q.w;
        return out;
    }
}
