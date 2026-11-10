package cn.judgfire;

/** Explicit gun-body attachment; independent of nearby moving magazines and decorative bones. */
public final class BodyEffectBinding {
  public int bone = -1;
  public int[][] triangles;
  public float[][] barycentric;
  public float normal_offset;

  public void validate(int count, MotionSkin.Mesh mesh, int bones) {
    if (bone >= 0) {
      if (bone >= bones || triangles != null || barycentric != null)
        throw new IllegalArgumentException("Invalid rigid effect binding");
      return;
    }
    if (mesh == null
        || triangles == null
        || barycentric == null
        || triangles.length != count
        || barycentric.length != count
        || !Float.isFinite(normal_offset)
        || normal_offset < 0
        || normal_offset > .05f)
      throw new IllegalArgumentException("Invalid surface effect binding");
    for (int i = 0; i < count; i++) {
      if (triangles[i].length != 3 || barycentric[i].length != 3)
        throw new IllegalArgumentException("Invalid effect triangle");
      float sum = 0;
      for (int j = 0; j < 3; j++) {
        if (triangles[i][j] < 0
            || triangles[i][j] >= mesh.vertices.length
            || !Float.isFinite(barycentric[i][j])
            || barycentric[i][j] < 0
            || barycentric[i][j] > 1)
          throw new IllegalArgumentException("Invalid surface coordinate");
        sum += barycentric[i][j];
      }
      if (Math.abs(sum - 1) > .00001f)
        throw new IllegalArgumentException("Unnormalized surface coordinate");
    }
  }

  /** Input and output use the effect JSON's Y-up coordinates, mesh skinning uses Y-down. */
  public float[][] deform(float[][] points, OriginalMotion.Frame frame) {
    if (frame == null) return points;
    float[][] result = new float[points.length][3];
    if (bone >= 0) {
      for (int i = 0; i < points.length; i++) {
        float[] p = points[i];
        result[i] = point(p[0], p[1], p[2], frame);
      }
      return result;
    }
    // Interpolate the already deformed surface, rather than interpolating bone weights
    // at an unrelated rest position. This stays attached even across a skinning seam.
    float[][] surface = MotionSkin.deform(frame.rig().smooth, frame.matrices());
    for (int i = 0; i < result.length; i++) {
      float[] a = surface[triangles[i][0]],
          b = surface[triangles[i][1]],
          c = surface[triangles[i][2]],
          w = barycentric[i];
      float nx = (b[1] - a[1]) * (c[2] - a[2]) - (b[2] - a[2]) * (c[1] - a[1]);
      float ny = (b[2] - a[2]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[2] - a[2]);
      float nz = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
      float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
      float offset = length > .000001f ? normal_offset / length : 0;
      result[i][0] = a[0] * w[0] + b[0] * w[1] + c[0] * w[2] + nx * offset;
      result[i][1] = -(a[1] * w[0] + b[1] * w[1] + c[1] * w[2] + ny * offset);
      result[i][2] = a[2] * w[0] + b[2] * w[1] + c[2] * w[2] + nz * offset;
    }
    return result;
  }

  public float[] point(float x, float y, float z, OriginalMotion.Frame frame) {
    if (frame == null) return new float[] {x, y, z};
    if (bone < 0) throw new IllegalStateException("Surface attachment needs a vertex index");
    float[] m = frame.matrices()[bone];
    return new float[] {
      m[0] * x - m[1] * y + m[2] * z + m[3],
      -(m[4] * x - m[5] * y + m[6] * z + m[7]),
      m[8] * x - m[9] * y + m[10] * z + m[11]
    };
  }
}
