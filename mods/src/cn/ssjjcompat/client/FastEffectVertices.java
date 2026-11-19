package cn.ssjjcompat.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.BufferBuilder;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;

/** Reuse the exact MC/JOML matrix operations without allocating vectors for every vertex. */
public final class FastEffectVertices {
    private static final class Scratch {
        final Vector3f samplePoint=new Vector3f();
        final Quaternionf sampleRotation=new Quaternionf();
    }
    private static final ThreadLocal<Scratch> SCRATCH=ThreadLocal.withInitial(Scratch::new);
    private record Overrides(boolean position,boolean normal) {}
    private static final ClassValue<Overrides> OVERRIDES=new ClassValue<>() {
        @Override protected Overrides computeValue(Class<?> type) {
            try {
                String p=null,n=null;
                for(var method:VertexConsumer.class.getMethods()) {
                    var parameters=method.getParameterTypes();
                    if(parameters.length!=4||parameters[1]!=float.class||parameters[2]!=float.class||parameters[3]!=float.class)continue;
                    if(parameters[0]==Matrix4f.class)p=method.getName();
                    if(parameters[0]==Matrix3f.class)n=method.getName();
                }
                if(p==null||n==null)throw new IllegalStateException("Missing MC matrix vertex API");
                return new Overrides(type.getMethod(p,Matrix4f.class,float.class,float.class,float.class).getDeclaringClass()!=VertexConsumer.class,
                    type.getMethod(n,Matrix3f.class,float.class,float.class,float.class).getDeclaringClass()!=VertexConsumer.class);
            } catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot inspect native vertex consumer",failure);}
        }
    };
    private FastEffectVertices() {}
    // Only used by the audited synchronous mesh loop. Values are copied into
    // independent returned float[] rows before the next point or particle.
    public static Vector3f samplePoint(float x,float y,float z) {
        return SCRATCH.get().samplePoint.set(x,y,z);
    }
    public static Quaternionf sampleRotation(float x,float y,float z,float w) {
        return SCRATCH.get().sampleRotation.set(x,y,z,w);
    }
    public static VertexConsumer vertex(VertexConsumer out,Matrix4f matrix,float x,float y,float z) {
        if(out.getClass()!=BufferBuilder.class&&OVERRIDES.get(out.getClass()).position)return out.vertex(matrix,x,y,z);
        // Exact Vector4f.mulAffine/mulGeneric xyz operation order for w=1,
        // including the installed JOML Math.fma option and float rounding.
        float px=org.joml.Math.fma(matrix.m00(),x,org.joml.Math.fma(matrix.m10(),y,org.joml.Math.fma(matrix.m20(),z,matrix.m30())));
        float py=org.joml.Math.fma(matrix.m01(),x,org.joml.Math.fma(matrix.m11(),y,org.joml.Math.fma(matrix.m21(),z,matrix.m31())));
        float pz=org.joml.Math.fma(matrix.m02(),x,org.joml.Math.fma(matrix.m12(),y,org.joml.Math.fma(matrix.m22(),z,matrix.m32())));
        return out.vertex(px,py,pz);
    }
    public static VertexConsumer normal(VertexConsumer out,Matrix3f matrix,float x,float y,float z) {
        if(out.getClass()!=BufferBuilder.class&&OVERRIDES.get(out.getClass()).normal)return out.normal(matrix,x,y,z);
        float nx=org.joml.Math.fma(matrix.m00(),x,org.joml.Math.fma(matrix.m10(),y,matrix.m20()*z));
        float ny=org.joml.Math.fma(matrix.m01(),x,org.joml.Math.fma(matrix.m11(),y,matrix.m21()*z));
        float nz=org.joml.Math.fma(matrix.m02(),x,org.joml.Math.fma(matrix.m12(),y,matrix.m22()*z));
        return out.normal(nx,ny,nz);
    }
}
