package ssjj.dark;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Private particle buffer and source-alpha material passes; never flushes TaCZ's builder. */
public final class DarkBladePass extends RenderType {
    private static MultiBufferSource.BufferSource isolated;
    private static boolean waitingLogged, submittedLogged;
    private static net.minecraft.client.renderer.ShaderInstance addShader, alphaShader;
    public static final class Shaders {
        @net.minecraftforge.eventbus.api.SubscribeEvent public static void register(net.minecraftforge.client.event.RegisterShadersEvent event) throws java.io.IOException {
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),new ResourceLocation("ssjj_dark","blade_add"),DefaultVertexFormat.f_85812_), next -> { addShader=next; com.mojang.logging.LogUtils.getLogger().info("[DarkBladeFX] Add shader ready"); });
            event.registerShader(new net.minecraft.client.renderer.ShaderInstance(event.getResourceProvider(),new ResourceLocation("ssjj_dark","blade_alpha"),DefaultVertexFormat.f_85812_), next -> { alphaShader=next; com.mojang.logging.LogUtils.getLogger().info("[DarkBladeFX] Alpha shader ready"); });
        }
    }
    private DarkBladePass(String name, RenderType base, boolean additive) {
        super(name, DefaultVertexFormat.f_85812_, VertexFormat.Mode.QUADS, 262144, false, false,
            () -> {
                base.m_110185_();
                RenderSystem.setShader(() -> additive ? addShader : alphaShader);
                RenderSystem.enableBlend();
                RenderSystem.blendFuncSeparate(770, additive ? 1 : 771, 1, 771);
                RenderSystem.disableCull(); RenderSystem.depthMask(false);
            }, base::m_110188_);
    }
    static RenderType create(ResourceLocation texture, boolean additive) {
        return new DarkBladePass("ssjj_dark_blade_" + (additive ? "add" : "alpha"),
            RenderType.m_110488_(texture), additive);
    }
    static void draw(DarkBladeEffects.Data data, int variant, Matrix4f matrix, boolean avatar, int overlay, float seconds) {
        if (addShader == null || alphaShader == null) {
            if(!waitingLogged) { waitingLogged=true; com.mojang.logging.LogUtils.getLogger().warn("[DarkBladeFX] Waiting for registered shaders"); }
            return;
        }
        if (isolated == null) isolated = DarkBladeEffects.newIsolatedBuffers();
        var shader = RenderSystem.getShader();
        int program=GL11.glGetInteger(35725), activeTexture=GL11.glGetInteger(34016);
        RenderSystem.activeTexture(33984);int boundTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);RenderSystem.activeTexture(activeTexture);
        int t0=RenderSystem.getShaderTexture(0), t1=RenderSystem.getShaderTexture(1), t2=RenderSystem.getShaderTexture(2);
        float[] color=RenderSystem.getShaderColor(); float r=color[0],g=color[1],b=color[2],a=color[3];
        boolean blending=GL11.glIsEnabled(GL11.GL_BLEND), culling=GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depth=GL11.glIsEnabled(GL11.GL_DEPTH_TEST), write=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunc=GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int sr=GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),dr=GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sa=GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),da=GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        try {
            RenderSystem.setShaderColor(1,1,1,1);
            DarkBladeEffects.submit(data,variant,matrix,avatar,isolated,overlay,seconds);
            if(!submittedLogged) { submittedLogged=true; com.mojang.logging.LogUtils.getLogger().info("[DarkBladeFX] Submitted after opaque blade: profile {}",variant); }
        } finally {
            isolated.m_109911_();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0,t0); RenderSystem.setShaderTexture(1,t1); RenderSystem.setShaderTexture(2,t2);
            RenderSystem.setShaderColor(r,g,b,a);
            if(shader!=null)shader.m_173363_();
            com.mojang.blaze3d.platform.GlStateManager._glUseProgram(program);
            RenderSystem.activeTexture(33984);RenderSystem.bindTexture(boundTexture);RenderSystem.activeTexture(activeTexture);
            RenderSystem.blendFuncSeparate(sr,dr,sa,da);
            if(blending)RenderSystem.enableBlend();else RenderSystem.disableBlend();
            if(culling)RenderSystem.enableCull();else RenderSystem.disableCull();
            if(depth)RenderSystem.enableDepthTest();else RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(depthFunc);RenderSystem.depthMask(write);
        }
    }
}
