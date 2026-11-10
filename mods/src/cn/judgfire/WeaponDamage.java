package cn.judgfire;

import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** Weapon death text and opt-in standard defense tags; damage still uses native hurt. */
@Mod.EventBusSubscriber(modid = JudgmentFlame.MODID)
public class WeaponDamage extends DamageSource {
  private static final String SHOT_NAME = "JudgfireShotWeaponName";
  private final DamageSource original;
  private final Component weapon;
  private final boolean crown;
  private final boolean piercing;

  WeaponDamage(DamageSource original, Component weapon, boolean crown) {
    this(original, weapon, crown, false);
  }

  WeaponDamage(DamageSource original, Component weapon, boolean crown, boolean piercing) {
    super(original.typeHolder(), original.getDirectEntity(), original.getEntity());
    this.original = original;
    this.weapon = weapon.copy();
    this.crown = crown;
    this.piercing = piercing;
  }

  static Component weaponName(ItemStack stack) {
    var base = Component.translatable("gun.judgfire.judgment_flame.name");
    return stack.hasCustomHoverName()
        ? Component.translatable("death.judgfire.named_weapon", base, stack.getHoverName().copy())
        : base;
  }

  static void tag(Entity bullet, ItemStack stack) {
    bullet.getPersistentData().putString(SHOT_NAME, Component.Serializer.toJson(weaponName(stack)));
  }

  private static Component shotName(Entity bullet) {
    if (bullet != null) {
      try {
        var saved = Component.Serializer.fromJson(bullet.getPersistentData().getString(SHOT_NAME));
        if (saved != null) return saved;
      } catch (RuntimeException ignored) {
        // Legacy or malformed cosmetic NBT must never prevent a native bullet hit.
      }
    }
    return Component.translatable("gun.judgfire.judgment_flame.name");
  }

  @SubscribeEvent(priority = EventPriority.LOWEST)
  public static void bullet(EntityHurtByGunEvent.Pre event) {
    if (event.isCanceled()
        || event.getLogicalSide() != LogicalSide.SERVER
        || !(event.getAttacker() instanceof ServerPlayer)
        || !JudgmentFlame.GUN.equals(event.getGunId())) return;
    var name = shotName(event.getBullet());
    for (var part : GunDamageSourcePart.values()) {
      var source = event.getDamageSource(part);
      if (source != null && !(source instanceof WeaponDamage))
        event.setDamageSource(
            part,
            new WeaponDamage(
                source,
                name,
                false,
                JudgmentPiercing.active(((ServerPlayer) event.getAttacker()).getMainHandItem())));
    }
    JudgmentDamage.gun(event);
  }

  static Component message(Component victim, Component attacker, Component weapon, boolean crown) {
    return Component.translatable(
            crown ? "death.judgfire.crown_prefix" : "death.judgfire.flame_prefix")
        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
        .append(
            Component.translatable(
                    crown ? "death.attack.judgfire.crown" : "death.attack.judgfire.flame",
                    victim.copy().withStyle(ChatFormatting.RED),
                    attacker.copy().withStyle(ChatFormatting.YELLOW),
                    Component.translatable("death.judgfire.weapon", weapon.copy())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .withStyle(ChatFormatting.GRAY)
                .withStyle(style -> style.withBold(false)));
  }

  @Override
  public Component getLocalizedDeathMessage(LivingEntity victim) {
    var attacker = getEntity() != null ? getEntity() : getDirectEntity();
    if (attacker == null) return original.getLocalizedDeathMessage(victim);
    return message(victim.getDisplayName(), attacker.getDisplayName(), weapon, crown);
  }

  @Override
  public float getFoodExhaustion() {
    return original.getFoodExhaustion();
  }

  @Override
  public boolean isIndirect() {
    return original.isIndirect();
  }

  @Override
  public Entity getDirectEntity() {
    return original.getDirectEntity();
  }

  @Override
  public Entity getEntity() {
    return original.getEntity();
  }

  @Override
  public String getMsgId() {
    return original.getMsgId();
  }

  @Override
  public boolean scalesWithDifficulty() {
    return original.scalesWithDifficulty();
  }

  @Override
  public boolean isCreativePlayer() {
    return original.isCreativePlayer();
  }

  @Override
  public Vec3 getSourcePosition() {
    return original.getSourcePosition();
  }

  @Override
  public Vec3 sourcePositionRaw() {
    return original.sourcePositionRaw();
  }

  @Override
  public boolean is(TagKey<DamageType> tag) {
    return (piercing
            && (tag == DamageTypeTags.BYPASSES_ARMOR
                || tag == DamageTypeTags.BYPASSES_SHIELD
                || tag == DamageTypeTags.BYPASSES_EFFECTS
                || tag == DamageTypeTags.BYPASSES_RESISTANCE
                || tag == DamageTypeTags.BYPASSES_ENCHANTMENTS
                || tag == DamageTypeTags.BYPASSES_COOLDOWN))
        || original.is(tag);
  }

  @Override
  public boolean is(ResourceKey<DamageType> key) {
    return original.is(key);
  }

  @Override
  public DamageType type() {
    return original.type();
  }

  @Override
  public Holder<DamageType> typeHolder() {
    return original.typeHolder();
  }
}
