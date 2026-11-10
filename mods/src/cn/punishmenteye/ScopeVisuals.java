package cn.punishmenteye;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
/** Original E008 T129 geometry follows the receiver pose, independent of grip and V timer. */
public final class ScopeVisuals {
    private static final ResourceLocation TEXTURE=new ResourceLocation("eyeofpunishment","textures/fx/scope_t129.png");
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new BufferBuilder(1024));
    public static void render(WeaponVisuals.Part mesh,PoseStack pose,int overlay){
        var type=RenderType.entityTranslucentEmissive(TEXTURE);
        WeaponVisuals.render(mesh,pose,BUFFERS.getBuffer(type),15728880,overlay);
        BUFFERS.endBatch();
    }
}
