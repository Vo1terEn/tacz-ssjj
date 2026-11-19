package cn.ssjjdimension.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** A private, bounded world-effect pass. Never flushes Minecraft's shared buffers. */
public final class DimensionSkillBatcher {
    private record Material(String texture,boolean additive) {}
    private record Draw(float[][] positions,float[][] tex,int[][] triangles,float[] color,float[] uv,
                        Matrix4f pose,Matrix3f normal,float depth,boolean facing) {}
    private record Run(Material material,List<Draw> draws) {}
    private final Map<Material,List<Draw>> objectGroups=new LinkedHashMap<>();
    private final Map<Material,List<Draw>> additiveGroups=new LinkedHashMap<>();
    private final List<Run> runs=new ArrayList<>();
    private int remaining,submitted;
    public DimensionSkillBatcher(int triangleBudget){remaining=Math.max(0,triangleBudget);}
    public int triangles(){return submitted;}
    public int materials(){return runs.size()+additiveGroups.size()+objectGroups.size();}
    public boolean hasBudget(){return remaining>=2;}
    public void beginObject(){if(!objectGroups.isEmpty())throw new IllegalStateException("Unfinished effect object");}
    private void drainAdditive(){for(var e:additiveGroups.entrySet())runs.add(new Run(e.getKey(),e.getValue()));additiveGroups.clear();}
    public void endObject(){
        // The old renderer groups materials inside each effect. Alpha materials
        // are ordering barriers; additive passes may share a submission only
        // within those barriers, never across a translucent surface.
        for(var e:objectGroups.entrySet()){
            if(e.getKey().additive)additiveGroups.computeIfAbsent(e.getKey(),unused->new ArrayList<>()).addAll(e.getValue());
            else {drainAdditive();runs.add(new Run(e.getKey(),e.getValue()));}
        }
        objectGroups.clear();
    }
    public boolean add(String texture,boolean additive,float[][] positions,float[][] tex,int[][] triangles,
                       float[] color,float[] uv,Matrix4f pose,Matrix3f normal,float depth,boolean facing){
        if(triangles.length>remaining){remaining=0;return false;}
        remaining-=triangles.length;submitted+=triangles.length;
        objectGroups.computeIfAbsent(new Material(texture,additive),key->new ArrayList<>())
            .add(new Draw(positions,tex,triangles,color,uv,pose,normal,depth,facing));return true;
    }
    public void flush(MultiBufferSource.BufferSource buffers){
        try {
            if(!objectGroups.isEmpty())endObject();drainAdditive();
            for(var run:runs){
                var key=run.material;var type=DimensionMarkRenderType.of(new ResourceLocation(key.texture),key.additive);
                var out=buffers.getBuffer(type);
                for(var draw:run.draws)for(var face:draw.triangles)for(int corner=0;corner<4;corner++){
                    int index=face[Math.min(corner,2)];var p=draw.positions[index];var tex=draw.tex[index];var c=draw.color;var uv=draw.uv;
                    out.vertex(draw.pose,p[0],p[1],p[2]*draw.depth).color(c[0],c[1],c[2],c[3])
                        .uv(tex[0]*uv[0]+uv[2],tex[1]*uv[1]-uv[3]).overlayCoords(0).uv2(15728880)
                        .normal(draw.normal,0,draw.facing?0:1,draw.facing?1:0).endVertex();
                }
                buffers.endBatch(type);
            }
        } finally {objectGroups.clear();additiveGroups.clear();runs.clear();}
    }
    public static MultiBufferSource.BufferSource buffers(){return MultiBufferSource.immediate(new BufferBuilder(65536));}
}
