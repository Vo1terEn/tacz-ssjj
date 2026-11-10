package cn.judgfire;

import java.util.Arrays;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

/** Original sampled bone matrices and continuous weighted skinning; no game state. */
public final class MotionSkin {
  public int version;
  public String[] bone_names;
  public float[][] cloud, cloud_weights;
  public int[][] cloud_indices;
  public Mesh smooth, pixel;
  public Clip[] clips;

  public static final class Mesh {
    public float[][] vertices, weights;
    public int[][] faces, indices;
    public float[][][] uv;
  }

  public static final class Clip {
    public int id;
    public String name;
    public float duration;
    public float[][][] frames;
    private transient Quaternionf[][] rotations;
  }

  public record Weights(int[] indices, float[] values) {}

  private transient Node tree;

  private static final class Node {
    int index, axis;
    Node left, right;

    Node(int index, int axis) {
      this.index = index;
      this.axis = axis;
    }
  }

  public void validate() {
    if (version != 1
        || bone_names.length != 11
        || cloud.length != cloud_indices.length
        || cloud.length != cloud_weights.length
        || (clips.length != 3 && clips.length != 4)) throw new IllegalArgumentException("Invalid original animation rig");
    for (var clip : clips) {
      if (clip.duration <= 0 || clip.frames.length < 2)
        throw new IllegalArgumentException("Invalid clip");
      clip.rotations = new Quaternionf[clip.frames.length][bone_names.length];
      for (int frame = 0; frame < clip.frames.length; frame++)
        for (int bone = 0; bone < bone_names.length; bone++) {
          float[] matrix = clip.frames[frame][bone];
          if (matrix.length != 12) throw new IllegalArgumentException("Invalid matrix");
          for (float f : matrix)
            if (!Float.isFinite(f)) throw new IllegalArgumentException("Nonfinite matrix");
          clip.rotations[frame][bone] =
              new Quaternionf()
                  .setFromNormalized(
                      new Matrix3f(
                          matrix[0],
                          matrix[4],
                          matrix[8],
                          matrix[1],
                          matrix[5],
                          matrix[9],
                          matrix[2],
                          matrix[6],
                          matrix[10]))
                  .normalize();
        }
    }
    Integer[] indices = new Integer[cloud.length];
    for (int i = 0; i < indices.length; i++) indices[i] = i;
    tree = build(indices, 0, indices.length, 0);
  }

  private Node build(Integer[] ids, int start, int end, int axis) {
    if (start == end) return null;
    Arrays.sort(ids, start, end, (a, b) -> Float.compare(cloud[a][axis], cloud[b][axis]));
    int mid = (start + end) >>> 1;
    var node = new Node(ids[mid], axis);
    node.left = build(ids, start, mid, (axis + 1) % 3);
    node.right = build(ids, mid + 1, end, (axis + 1) % 3);
    return node;
  }

  public Weights weights(float x, float y, float z) {
    float[] point = {x, y, z}, best = {Float.POSITIVE_INFINITY, 0};
    nearest(tree, point, best);
    int index = (int) best[1];
    return new Weights(cloud_indices[index], cloud_weights[index]);
  }

  private void nearest(Node node, float[] point, float[] best) {
    if (node == null) return;
    float[] p = cloud[node.index];
    float dx = point[0] - p[0], dy = point[1] - p[1], dz = point[2] - p[2];
    float dist = dx * dx + dy * dy + dz * dz;
    if (dist < best[0]) {
      best[0] = dist;
      best[1] = node.index;
    }
    float delta = point[node.axis] - p[node.axis];
    Node first = delta < 0 ? node.left : node.right, second = delta < 0 ? node.right : node.left;
    nearest(first, point, best);
    if (delta * delta <= best[0]) nearest(second, point, best);
  }

  public float[][] sample(int id, float time, float weight) {
    Clip clip = null;
    for (var entry : clips) if (entry.id == id) clip = entry;
    if (clip == null || !Float.isFinite(time) || !Float.isFinite(weight)) return null;
    float progress = Math.max(0, Math.min(1, time / clip.duration)) * (clip.frames.length - 1);
    int a = (int) progress, b = Math.min(a + 1, clip.frames.length - 1);
    float t = progress - a;
    weight = Math.max(0, Math.min(1, weight));
    float[][] result = new float[bone_names.length][12];
    for (int bone = 0; bone < result.length; bone++) {
      // Interpolating matrix entries shrinks/warps rigid parts during fast turns
      // and track fades. Keep rotations on SO(3); blend only translations linearly.
      var rotation = new Quaternionf(clip.rotations[a][bone]).slerp(clip.rotations[b][bone], t);
      var matrix = new Matrix3f().set(new Quaternionf().slerp(rotation, weight));
      result[bone] =
          new float[] {
            matrix.m00(),
            matrix.m10(),
            matrix.m20(),
            0,
            matrix.m01(),
            matrix.m11(),
            matrix.m21(),
            0,
            matrix.m02(),
            matrix.m12(),
            matrix.m22(),
            0
          };
      for (int j : new int[] {3, 7, 11})
        result[bone][j] =
            (clip.frames[a][bone][j] * (1 - t) + clip.frames[b][bone][j] * t) * weight;
    }
    return result;
  }

  public static void deform(
      float[][] matrices, int[] indices, float[] weights, float x, float y, float z, float[] out) {
    out[0] = out[1] = out[2] = 0;
    float total = 0;
    for (int i = 0; i < weights.length; i++) {
      float w = weights[i];
      if (w <= 0) continue;
      float[] m = matrices[indices[i]];
      out[0] += (m[0] * x + m[1] * y + m[2] * z + m[3]) * w;
      out[1] += (m[4] * x + m[5] * y + m[6] * z + m[7]) * w;
      out[2] += (m[8] * x + m[9] * y + m[10] * z + m[11]) * w;
      total += w;
    }
    if (total > 0) {
      out[0] /= total;
      out[1] /= total;
      out[2] /= total;
    } else {
      out[0] = x;
      out[1] = y;
      out[2] = z;
    }
  }

  public static float[][] deform(Mesh mesh, float[][] matrices) {
    float[][] result = new float[mesh.vertices.length][3];
    for (int i = 0; i < result.length; i++) {
      float[] p = mesh.vertices[i];
      deform(matrices, mesh.indices[i], mesh.weights[i], p[0], p[1], p[2], result[i]);
    }
    return result;
  }
}
