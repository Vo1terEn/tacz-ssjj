package cn.judgfire;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

/** ShaderInstance.apply runs after RenderType state setup. Its JSON blend can
 * otherwise override the source material blend, including disabling it for an
 * omitted JSON blend. Apply each material's factors after that engine step. */
public final class MaterialBlendShader extends ShaderInstance {
  private int srcRgb=770,dstRgb=771,srcAlpha=1,dstAlpha=771;
  public MaterialBlendShader(ResourceProvider resources,ResourceLocation name,VertexFormat format)throws IOException {
    super(resources,name,format);
  }
  public void setBlend(int srcRgb,int dstRgb,int srcAlpha,int dstAlpha) {
    this.srcRgb=srcRgb;this.dstRgb=dstRgb;this.srcAlpha=srcAlpha;this.dstAlpha=dstAlpha;
  }
  @Override public void apply() {
    super.apply();
    RenderSystem.enableBlend();
    RenderSystem.blendEquation(32774); // GL_FUNC_ADD, independent of prior shaders.
    RenderSystem.blendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);
  }
}
