package ssjj.dark;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockGunModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** 用原生玩家网格/皮肤绘制源动作手臂，避免PlayerRenderer再次叠加手臂摆动。 */
final class DarkNativeHands {
    private static ModelPart wide, slim;

    static void bind(BedrockGunModel model) {
        Binding binding = new Binding(model);
        for (boolean right : new boolean[] { false, true }) {
            model.setFunctionalRenderer(right ? "righthand_pos" : "lefthand_pos", part -> (stack, ignored, context, light, overlay) -> {
                if (!context.m_269069_() || !model.getRenderHand()) return;
                binding.capture(right ? 1 : 0, stack, light, overlay);
            });
        }
    }

    /** Both hands are emitted after the weapon, sharing one skin buffer flush.
     * Matrices and PoseStack live with this model rather than each frame. */
    private static final class Binding {
        private final BedrockGunModel model;
        private final Matrix4f[] matrices = { new Matrix4f(), new Matrix4f() };
        private final Matrix3f[] normals = { new Matrix3f(), new Matrix3f() };
        private final boolean[] ready = new boolean[2];
        private final int[] lights = new int[2], overlays = new int[2];
        private final PoseStack stack = new PoseStack();
        private boolean queued;
        private final com.tacz.guns.client.model.IFunctionalRenderer deferred = (pose, consumer, context, light, overlay) -> draw();

        Binding(BedrockGunModel model) { this.model = model; }

        void capture(int hand, PoseStack pose, int light, int overlay) {
            matrices[hand].set(pose.m_85850_().m_252922_()); normals[hand].set(pose.m_85850_().m_252943_());
            ready[hand] = true; lights[hand] = light; overlays[hand] = overlay;
            if (!queued) { queued = true; model.delegateRender(deferred); }
        }

        void draw() {
            try {
                Minecraft mc = Minecraft.m_91087_();
                var player = mc.f_91074_;
                if (player == null) return;
                boolean narrow = player.m_108564_().equals("slim");
                ModelPart body = root(narrow);
                var buffers = mc.m_91269_().m_110104_();
                var type = RenderType.m_110458_(player.m_108560_());
                var out = buffers.m_6299_(type);
                for (int hand = 0; hand < 2; hand++) {
                    if (!ready[hand]) continue;
                    stack.m_85850_().m_252922_().set(matrices[hand]); stack.m_85850_().m_252943_().set(normals[hand]);
                    if (narrow) stack.m_85837_((hand == 1 ? .5 : -.5) / 16, .5 / 16, 0);
                    stack.m_252781_(Axis.f_252403_.m_252977_(180));
                    body.m_171324_(hand == 1 ? "right_arm" : "left_arm").m_104301_(stack, out, lights[hand], overlays[hand]);
                    body.m_171324_(hand == 1 ? "right_sleeve" : "left_sleeve").m_104301_(stack, out, lights[hand], overlays[hand]);
                }
                buffers.m_109912_(type);
            } finally { queued = false; ready[0] = ready[1] = false; }
        }
    }

    static ModelPart root(boolean narrow) {
        if (narrow) {
            if (slim == null) slim = PlayerModel.m_170825_(CubeDeformation.f_171458_, true).m_171576_().m_171583_(64, 64);
            return slim;
        }
        if (wide == null) wide = PlayerModel.m_170825_(CubeDeformation.f_171458_, false).m_171576_().m_171583_(64, 64);
        return wide;
    }

    private DarkNativeHands() {}
}
