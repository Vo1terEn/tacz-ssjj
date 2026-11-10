package cn.ssjjcompat;

import com.mojang.blaze3d.vertex.*;
import com.tacz.guns.client.model.bedrock.*;
import com.tacz.guns.client.model.FunctionalBedrockPart;
import net.minecraft.world.item.ItemDisplayContext;
import java.lang.reflect.*;
import java.util.*;

/** Measures native cube/functional vertices without textures or GPU submission. */
public final class PreviewGeometry {
    private PreviewGeometry(){}
    private static final Map<BedrockModel,double[]> CACHE=Collections.synchronizedMap(new WeakHashMap<>());
    public static void clear(){CACHE.clear();}
    public static double[] bounds(BedrockModel model){
        if(model==null)return null;
        if(model instanceof com.tacz.guns.client.model.BedrockAttachmentModel){try{var field=model.getClass().getDeclaredField("currentGunItem");field.setAccessible(true);field.set(model,null);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
        double[] cached=CACHE.get(model);if(cached!=null)return cached.length==0?null:cached;
        double[] b=capture(out->{PoseStack pose=new PoseStack();for(var root:model.getShouldRender())emit(root,pose,out);});
        if(b==null)return null;CACHE.put(model,b);return b;
    }
    public static double[] slotBounds(){
        try{var field=com.tacz.guns.client.model.SlotModel.class.getDeclaredField("bone");field.setAccessible(true);var node=(BedrockPart)field.get(com.tacz.guns.client.renderer.item.AttachmentItemRenderer.SLOT_ATTACHMENT_MODEL);return capture(out->emit(node,new PoseStack(),out));}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static double[] capture(java.util.function.Consumer<VertexConsumer> emitter){
        double[] b={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
        VertexConsumer consumer=(VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),new Class<?>[]{VertexConsumer.class},(proxy,method,args)->{
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
            var p=method.getParameterTypes();
            if(p.length==3&&p[0]==double.class&&p[1]==double.class&&p[2]==double.class)for(int i=0;i<3;i++){double v=((Number)args[i]).doubleValue();if(!Double.isFinite(v))throw new IllegalArgumentException("Nonfinite preview vertex");b[i]=Math.min(b[i],v);b[i+3]=Math.max(b[i+3],v);}
            return method.getReturnType()==VertexConsumer.class?proxy:null;
        });
        emitter.accept(consumer);
        if(!Double.isFinite(b[0]))return null;
        return b;
    }
    public static void emit(BedrockPart node,PoseStack pose,VertexConsumer consumer){
        pose.pushPose();try{
            node.translateAndRotateAndScale(pose);
            if(node instanceof FunctionalBedrockPart functional&&functional.functionalRenderer!=null){
                var renderer=functional.functionalRenderer.apply(node);if(renderer!=null)renderer.render(pose,consumer,ItemDisplayContext.FIXED,0xf000f0,0);
            }
            if(node.visible){node.compile(pose.last(),consumer,0xf000f0,0,1,1,1,1);for(var child:node.children)emit(child,pose,consumer);}
        }finally{pose.popPose();}
    }
    public static void fit(PoseStack pose,double[] b){
        double radius=0;for(int i=0;i<3;i++)radius+=Math.pow((b[i+3]-b[i])*.5,2);
        float scale=(float)(.52/Math.max(.001,Math.sqrt(radius)));pose.scale(scale,scale,scale);
        pose.translate(-(b[0]+b[3])*.5,-(b[1]+b[4])*.5,-(b[2]+b[5])*.5);
    }
}
