package ssjj.dark;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import java.util.LinkedHashMap;

/** 技能期间用玩家自己的皮肤/盔甲绘制分段MC肢体；源上下身动作驱动屈膝和屈肘。 */
@Mod.EventBusSubscriber(modid = DarkCrystalMod.ID, value = Dist.CLIENT)
public final class DarkAvatarRenderer {
    private static final int[] QUAD_CORNERS = { 0, 1, 2, 2 };
    private static final EquipmentSlot[] ARMOR_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final LinkedHashMap<String, ResourceLocation[]> ARMOR_TEXTURES = new LinkedHashMap<>(64, .75f, true);
    private static float[] avatarPose = new float[0], weaponPose = new float[0], centers = new float[0], rightAxes = new float[0];
    private static final ArmorLayer[] LAYERS = { new ArmorLayer(), new ArmorLayer(), new ArmorLayer(), new ArmorLayer() };
    private static final class ArmorLayer {
        int bit, tint; float scale; RenderType base, overlay;
    }
    @SubscribeEvent
    public static void player(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        DarkPresentation.View view = DarkPresentation.view(player, event.getPartialTick());
        if (view == null) return;
        var data = DarkRuntimeAssets.get();
        preparePose(data, view.sourceSeconds(), view.recovery());
        prepareArmor(event, player);
        boolean slim = player.m_108564_().equals("slim");
        event.setCanceled(true);
        var stack = event.getPoseStack();
        stack.m_85836_();
        try {
            stack.m_252781_(com.mojang.math.Axis.f_252436_.m_252977_(-view.cast().yaw()));
            Matrix4f matrix = stack.m_85850_().m_252922_();
            VertexConsumer skin = event.getMultiBufferSource().m_6299_(RenderType.m_110458_(player.m_108560_()));
            for (int part = 0; part < data.parts.length; part++) {
                drawPart(skin, matrix, data, part, event.getPackedLight(), 1, 0, 0, false, 0xffffff, slim);
                String name = data.parts[part].name();
                float du = name.equals("head") ? .5f : name.startsWith("L_arm") || name.startsWith("L_forearm") ? .25f : 0;
                float dv = name.equals("head") ? 0 : name.startsWith("L_arm") || name.startsWith("L_forearm") ? 0 : name.startsWith("L_") ? 0 : .25f;
                if (name.startsWith("L_thigh") || name.startsWith("L_calf")) du = -.25f;
                drawPart(skin, matrix, data, part, event.getPackedLight(), 1.025f, du, dv, false, 0xffffff, slim);
            }
            // Acquire one texture buffer at a time. MultiBufferSource may reuse
            // its fallback builder when the render type changes.
            for (ArmorLayer layer : LAYERS) {
                if (layer.base == null) continue;
                drawArmor(event.getMultiBufferSource().m_6299_(layer.base), matrix, data, layer.bit, event.getPackedLight(), layer.scale, layer.tint);
                if (layer.overlay != null) drawArmor(event.getMultiBufferSource().m_6299_(layer.overlay), matrix, data, layer.bit, event.getPackedLight(), 1.105f, 0xffffff);
            }
            ResourceLocation bladeTexture = DarkSkins.bladeTexture(player.m_21205_());
            VertexConsumer blade = event.getMultiBufferSource().m_6299_(RenderType.m_110458_(bladeTexture));
            // VertexConsumer使用QUADS；三角面末点重复一次，保持原刀体三角形UV。
            for (int i = 0; i < data.weaponFaces.length; i += 3) {
                for (int corner : QUAD_CORNERS) {
                    int v = data.weaponFaces[i + corner];
                    blade.m_252986_(matrix, weaponPose[v * 3], weaponPose[v * 3 + 1], weaponPose[v * 3 + 2])
                        .m_85950_(1, 1, 1, 1).m_7421_(data.weaponUV[v * 2], 1 - data.weaponUV[v * 2 + 1])
                        .m_86008_(655360).m_85969_(event.getPackedLight()).m_5601_(0, 1, 0).m_5752_();
                }
            }
            DarkBladeEffects.drawAvatar(matrix, player.m_21205_(), weaponPose, event.getMultiBufferSource(),
                655360, (player.m_9236_().m_46467_() + event.getPartialTick()) / 20f);
        } finally { stack.m_85849_(); for (ArmorLayer layer : LAYERS) { layer.base = null; layer.overlay = null; } }
    }

    private static void preparePose(DarkRuntimeAssets.Data data, float seconds, float recovery) {
        if (avatarPose.length != data.avatar[0].length) {
            avatarPose = new float[data.avatar[0].length]; centers = new float[data.parts.length * 3]; rightAxes = new float[centers.length];
        }
        if (weaponPose.length != data.weapon[0].length) weaponPose = new float[data.weapon[0].length];
        DarkRuntimeAssets.sampleInto(data.avatar, seconds, recovery, avatarPose);
        DarkRuntimeAssets.sampleInto(data.weapon, seconds, recovery, weaponPose);
        for (int part = 0; part < data.parts.length; part++) {
            int base = part * 72, center = part * 3;
            for (int axis = 0; axis < 3; axis++) {
                float value = 0;
                for (int vertex = 0; vertex < 24; vertex++) value += avatarPose[base + vertex * 3 + axis] / 24;
                centers[center + axis] = value;
                rightAxes[center + axis] = avatarPose[base + 3 + axis] - avatarPose[base + axis];
            }
            float x = rightAxes[center], y = rightAxes[center + 1], z = rightAxes[center + 2];
            float lengthSquared = x * x + y * y + z * z;
            if (lengthSquared > .000001f) {
                float inverse = 1f / (float)Math.sqrt(lengthSquared);
                rightAxes[center] *= inverse; rightAxes[center + 1] *= inverse; rightAxes[center + 2] *= inverse;
            }
        }
    }

    private static void prepareArmor(RenderPlayerEvent.Pre event, AbstractClientPlayer player) {
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            EquipmentSlot slot = ARMOR_SLOTS[index]; ArmorLayer layer = LAYERS[index];
            layer.base = null; layer.overlay = null; layer.bit = 1 << index; layer.scale = slot == EquipmentSlot.LEGS ? 1.05f : 1.10f;
            var item = player.m_6844_(slot);
            if (!(item.m_41720_() instanceof ArmorItem armor)) continue;
            String material = armor.m_40401_().m_6082_();
            String key = material + (slot == EquipmentSlot.LEGS ? ":2" : ":1");
            ResourceLocation[] textures = ARMOR_TEXTURES.get(key);
            if (textures == null) {
                if (ARMOR_TEXTURES.size() >= 64) ARMOR_TEXTURES.remove(ARMOR_TEXTURES.keySet().iterator().next());
                ResourceLocation id = new ResourceLocation(material);
                String prefix = "textures/models/armor/" + id.m_135815_() + "_layer_" + (slot == EquipmentSlot.LEGS ? 2 : 1);
                textures = new ResourceLocation[] { new ResourceLocation(id.m_135827_(), prefix + ".png"), new ResourceLocation(id.m_135827_(), prefix + "_overlay.png") };
                ARMOR_TEXTURES.put(key, textures);
            }
            layer.base = RenderType.m_110458_(textures[0]);
            layer.tint = item.m_41720_() instanceof DyeableLeatherItem leather ? leather.m_41121_(item) : 0xffffff;
            if (item.m_41720_() instanceof DyeableLeatherItem) layer.overlay = RenderType.m_110458_(textures[1]);
        }
    }

    private static void drawArmor(VertexConsumer out, Matrix4f matrix, DarkRuntimeAssets.Data data, int bit, int light, float scale, int tint) {
        for (int part = 0; part < data.parts.length; part++) {
            if ((data.parts[part].armor() & bit) == 0) continue;
            String name = data.parts[part].name();
            float ax = name.startsWith("L_arm") || name.startsWith("L_forearm") ? .125f : name.startsWith("L_thigh") || name.startsWith("L_calf") ? -.25f : 0;
            float ay = name.startsWith("L_") ? -.5f : 0;
            drawPart(out, matrix, data, part, light, scale, ax, ay, true, tint, false);
        }
    }

    private static void drawPart(VertexConsumer out, Matrix4f matrix, DarkRuntimeAssets.Data data, int part, int light, float expand, float du, float dv, boolean armor, int rgb, boolean slim) {
        int center = part * 3;
        float centerX = centers[center], centerY = centers[center + 1], centerZ = centers[center + 2];
        String name = data.parts[part].name();
        boolean narrow = slim && !armor && (name.endsWith("_arm") || name.endsWith("_forearm"));
        float rightX = rightAxes[center], rightY = rightAxes[center + 1], rightZ = rightAxes[center + 2];
        for (int v = 0; v < 24; v++) {
            int offset = (part * 24 + v) * 3;
            float x = centerX + (avatarPose[offset] - centerX) * expand;
            float y = centerY + (avatarPose[offset + 1] - centerY) * expand;
            float z = centerZ + (avatarPose[offset + 2] - centerZ) * expand;
            if (narrow) {
                float along = (x - centerX) * rightX + (y - centerY) * rightY + (z - centerZ) * rightZ;
                x -= rightX * along * .25f; y -= rightY * along * .25f; z -= rightZ * along * .25f;
            }
            float u = data.parts[part].uv()[v * 2] + du, t = data.parts[part].uv()[v * 2 + 1] + dv;
            if (narrow) {
                float base = (name.startsWith("L_") ? 32 : 40) / 64f + du;
                float pixel = (u - base) * 64;
                if (pixel > 12) pixel = 11 + (pixel - 12) * .75f;
                else if (pixel > 8) pixel = 7 + pixel - 8;
                else if (pixel > 4) pixel = 4 + (pixel - 4) * .75f;
                u = base + pixel / 64;
            }
            out.m_252986_(matrix, x, y, z).m_85950_((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1)
                .m_7421_(u, armor ? t * 2 : t).m_86008_(655360).m_85969_(light).m_5601_(0, 1, 0).m_5752_();
        }
    }
    private DarkAvatarRenderer() {}
}
