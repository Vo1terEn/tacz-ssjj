package cn.ssjjares.client;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Original accessory geometry is immutable; no per-frame parser, skinning or allocation. */
public final class AresAccessoryMesh {
    static final String[] NAMES={"conquest_will","extinction_resolve","ruin_wings"};
    static final AresAccessoryMesh[] CACHE=new AresAccessoryMesh[6];static final boolean[] FAILED=new boolean[6];
    static final RenderType[] TYPES=new RenderType[6];
    final int vertices;final float[] data,origin;final int[] indices;final float vanillaScale;
    AresAccessoryMesh(int vertices,float[] data,int[] indices,float[] origin,float scale){this.vertices=vertices;this.data=data;this.indices=indices;this.origin=origin;vanillaScale=scale;}
    public static void reload(){for(int i=0;i<6;i++){CACHE[i]=null;FAILED[i]=false;}}
    static int index(int kind,int tier){return (kind-1)*2+tier-1;}
    public static String name(int kind,int tier){return NAMES[kind-1]+(tier==2?"_absolute":"");}
    public static AresAccessoryMesh get(int kind,int tier){
        int i=index(kind,tier);if(CACHE[i]!=null||FAILED[i])return CACHE[i];
        try{var id=new ResourceLocation("ssjj_ares","models/accessory/"+name(kind,tier)+".bin");try(var in=Minecraft.getInstance().getResourceManager().getResource(id).orElseThrow().open()){return CACHE[i]=read(in);}}
        catch(Exception failure){FAILED[i]=true;LogUtils.getLogger().error("Ares accessory mesh missing; using pixel fallback: "+name(kind,tier),failure);return null;}
    }
    static float finite(float f)throws IOException{if(!Float.isFinite(f))throw new IOException("Non-finite accessory field");return f;}
    public static AresAccessoryMesh read(InputStream input)throws IOException{
        try(var in=new DataInputStream(input)){
            if(in.readInt()!=0x41435231||in.readInt()!=2)throw new IOException("Accessory mesh version");
            int vertices=in.readInt(),count=in.readInt();if(vertices<3||vertices>20000||count<3||count>60000||count%3!=0)throw new IOException("Accessory mesh bounds");
            float[] origin={finite(in.readFloat()),finite(in.readFloat()),finite(in.readFloat())};float scale=finite(in.readFloat());if(scale<.5f||scale>3)throw new IOException("Accessory vanilla scale");
            float[] data=new float[vertices*8];for(int i=0;i<data.length;i++)data[i]=finite(in.readFloat());
            for(int i=0;i<vertices;i++){int p=i*8;if(Math.abs(data[p])+Math.abs(data[p+1])+Math.abs(data[p+2])>10)throw new IOException("Accessory vertex bounds");float n=data[p+3]*data[p+3]+data[p+4]*data[p+4]+data[p+5]*data[p+5];if(Math.abs(n-1)>.002f)throw new IOException("Accessory unit normal");}
            int[] indices=new int[count];for(int i=0;i<count;i++){indices[i]=in.readInt();if(indices[i]<0||indices[i]>=vertices)throw new IOException("Accessory vertex index");}if(in.read()!=-1)throw new IOException("Trailing accessory data");
            return new AresAccessoryMesh(vertices,data,indices,origin,scale);
        }
    }
    public void vanillaMount(PoseStack pose){pose.scale(-vanillaScale,-vanillaScale,vanillaScale);pose.translate(-origin[0],-origin[1],-origin[2]);}
    public void render(PoseStack pose,MultiBufferSource buffers,int light,int kind,int tier){
        int i=index(kind,tier);RenderType type=TYPES[i];
        if(type==null)TYPES[i]=type=RenderType.entityCutoutNoCull(new ResourceLocation("ssjj_ares","textures/models/accessory/"+name(kind,tier)+".png"));
        draw(pose,buffers.getBuffer(type),light,OverlayTexture.NO_OVERLAY);
    }
    public void draw(PoseStack pose,VertexConsumer out,int light,int overlay){
        var p=pose.last();for(int f=0;f<indices.length;f+=3)for(int c=0;c<4;c++){
            int k=indices[f+Math.min(c,2)]*8;
            out.vertex(p.pose(),data[k],data[k+1],data[k+2]).color(1f,1f,1f,1f).uv(data[k+6],data[k+7]).overlayCoords(overlay).uv2(light).normal(p.normal(),data[k+3],data[k+4],data[k+5]).endVertex();
        }
    }
}
