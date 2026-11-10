package cn.judgfire;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.renderer.entity.EntityBulletRenderer;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Retains native rendering for every ordinary bullet; skins use their snapshot display ID. */
public final class QinglongBulletRenderer extends EntityBulletRenderer {
  public QinglongBulletRenderer(EntityRendererProvider.Context context) {
    super(context);
  }

  @Override
  public void render(
      EntityKineticBullet bullet,
      float yaw,
      float partial,
      PoseStack pose,
      MultiBufferSource buffers,
      int light) {
    if (!JudgmentFlame.GUN.equals(bullet.getGunId())
        || WeaponSkins.displaySkin(bullet.getGunDisplayId()) == 0) {
      super.render(bullet, yaw, partial, pose, buffers, light);
      return;
    }
    if (WeaponSkins.displaySkin(bullet.getGunDisplayId()) == 3) {
      LightningVisuals.bullet(
          pose,
          buffers,
          Math.floorMod(bullet.tickCount, 16),
          Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
      renderTracerAmmo(bullet, new float[] {.64f, .52f, 1f}, partial, pose, light);
      return;
    }
    if (WeaponSkins.displaySkin(bullet.getGunDisplayId()) == 2) {
      // E211 native icy bolt sheet, captured by the bullet's immutable display ID.
      FrozenVisuals.bullet(
          pose,
          buffers,
          Math.floorMod(bullet.tickCount, 16),
          Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
      renderTracerAmmo(bullet, new float[] {.42f, .80f, 1f}, partial, pose, light);
      return;
    }
    QinglongVisuals.dragon(
        pose,
        Vec3.ZERO,
        Minecraft.getInstance().gameRenderer.getMainCamera().rotation(),
        .32f,
        1,
        buffers.getBuffer(RenderType.entityTranslucentEmissive(QinglongVisuals.DRAGON)),
        false);
    renderTracerAmmo(bullet, new float[] {.12f, 1, .87f}, partial, pose, light);
  }

  @Mod.EventBusSubscriber(
      modid = JudgmentFlame.MODID,
      value = Dist.CLIENT,
      bus = Mod.EventBusSubscriber.Bus.MOD)
  public static final class Registration {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
      event.registerEntityRenderer(EntityKineticBullet.TYPE, QinglongBulletRenderer::new);
    }
  }
}
