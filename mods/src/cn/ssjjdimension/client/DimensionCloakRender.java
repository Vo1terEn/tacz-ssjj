package cn.ssjjdimension.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Keeps the native TaCZ hand pass intact, applying alpha at its actual buffered draw. */
public final class DimensionCloakRender {
 static final float ALPHA=.5f;
 private static int handDepth;
 private DimensionCloakRender(){}

 /** The sole original Forge hand event is posted once, inside a bounded presentation scope. */
 public static boolean postHand(IEventBus bus,Event event){
  var mc=Minecraft.getInstance();
  if(!(event instanceof RenderHandEvent)||!DimensionDecoyClient.hidden(mc.player))return bus.post(event);
  // TaCZ obtains Minecraft's global buffer itself; wrapping the event buffer would miss it.
  return handPass(()->bus.post(event),()->mc.renderBuffers().bufferSource().endBatch());
 }
 static boolean active(){return handDepth>0;}
 static boolean handPass(BooleanSupplier post,Runnable flush){
  flush.run(); // Finish any world geometry before the first-person alpha scope.
  handDepth++;
  try{return post.getAsBoolean();}
  finally{try{flush.run();}finally{handDepth--;}}
 }
 record State(float[] color,boolean blend,int srcRgb,int dstRgb,int srcAlpha,int dstAlpha){}
 interface Graphics {
  State snapshot();
  void translucent(float alpha,State old);
  void restore(State old);
 }
 private static final Graphics GPU=new Graphics(){
  public State snapshot(){return new State(RenderSystem.getShaderColor().clone(),GL11.glIsEnabled(GL11.GL_BLEND),GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA));}
  public void translucent(float alpha,State old){
   RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
   var color=old.color();RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]*alpha);
  }
  public void restore(State old){
   var color=old.color();RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
   RenderSystem.blendFuncSeparate(old.srcRgb(),old.dstRgb(),old.srcAlpha(),old.dstAlpha());
   if(old.blend())RenderSystem.enableBlend();else RenderSystem.disableBlend();
  }
 };
 static void transparentDraw(Runnable draw,Graphics graphics){
  if(!active()){draw.run();return;}
  var old=graphics.snapshot();
  try{graphics.translucent(ALPHA,old);draw.run();}
  finally{graphics.restore(old);}
 }
 /** Called after RenderType.setupRenderState, so opaque cutout types also actually blend. */
 public static void draw(BufferBuilder.RenderedBuffer buffer){
  transparentDraw(()->BufferUploader.drawWithShader(buffer),GPU);
 }
}
