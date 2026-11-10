package cn.punishmenteye;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Presentation-only source decorator. Vanilla owns death, cancellation and message delivery. */
public final class EyeSkillDamage extends DamageSource {
  private final DamageSource original;
  private final boolean cloud;

  private EyeSkillDamage(DamageSource original, boolean cloud) {
    super(original.m_269150_(), original.m_7640_(), original.m_7639_());
    this.original = original;
    this.cloud = cloud;
  }

  public static DamageSource wrap(DamageSource original, String type) {
    if (original == null || original instanceof EyeSkillDamage) return original;
    if ("nofa_lightning".equals(type)) return new EyeSkillDamage(original, false);
    if ("nofa_cloud".equals(type)) return new EyeSkillDamage(original, true);
    return original;
  }

  static Component message(Component victim, Component attacker, boolean cloud) {
    int prefix = cloud ? 0xBD8AFF : 0x79B8FF;
    int weapon = cloud ? 0x79B8FF : 0xBD8AFF;
    return Component.m_237115_(cloud
            ? "death.eyeofpunishment.cloud_prefix" : "death.eyeofpunishment.lightning_prefix")
        .m_130938_(style -> style.m_178520_(prefix).m_131136_(true))
        .m_7220_(Component.m_237110_(cloud
                ? "death.attack.eyeofpunishment.cloud" : "death.attack.eyeofpunishment.lightning",
            victim.m_6881_().m_130938_(style -> style.m_178520_(0xD3AEFF)),
            attacker.m_6881_().m_130938_(style -> style.m_178520_(0x6FCCFF)),
            Component.m_237110_("death.eyeofpunishment.weapon",
                    Component.m_237115_("death.eyeofpunishment.weapon_name"))
                .m_130938_(style -> style.m_178520_(weapon).m_131136_(true)))
            .m_130938_(style -> style.m_178520_(0xB7B4F7).m_131136_(false)));
  }

  @Override public Component m_6157_(LivingEntity victim) {
    Entity attacker = m_7639_() != null ? m_7639_() : m_7640_();
    return attacker == null ? original.m_6157_(victim)
        : message(victim.m_5446_(), attacker.m_5446_(), cloud);
  }
  @Override public float m_19377_() { return original.m_19377_(); }
  @Override public boolean m_269014_() { return original.m_269014_(); }
  @Override public Entity m_7640_() { return original.m_7640_(); }
  @Override public Entity m_7639_() { return original.m_7639_(); }
  @Override public String m_19385_() { return original.m_19385_(); }
  @Override public boolean m_7986_() { return original.m_7986_(); }
  @Override public boolean m_19390_() { return original.m_19390_(); }
  @Override public Vec3 m_7270_() { return original.m_7270_(); }
  @Override public Vec3 m_269181_() { return original.m_269181_(); }
  @Override public boolean m_269533_(TagKey<DamageType> tag) { return original.m_269533_(tag); }
  @Override public boolean m_276093_(ResourceKey<DamageType> key) { return original.m_276093_(key); }
  @Override public DamageType m_269415_() { return original.m_269415_(); }
  @Override public Holder<DamageType> m_269150_() { return original.m_269150_(); }
}
