package cn.judgfire;

import com.tacz.guns.api.item.IGun;
import cn.fusionarsenal.UpgradeCatalog;
import com.tacz.guns.api.item.attachment.AttachmentType;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;

/** Native attributes remain extensible by commands and other mods. */
public final class EnergyAttributes {
  public static final ResourceLocation ATTACHMENT = LaserAttachments.ENERGY;
  public static final DeferredRegister<Attribute> ATTRIBUTES =
      DeferredRegister.create(ForgeRegistries.ATTRIBUTES, "judgfire_runtime");
  public static final RegistryObject<Attribute> RESISTANCE =
      ATTRIBUTES.register("energy_resistance", () -> attribute("energy_resistance"));
  public static final RegistryObject<Attribute> PENETRATION =
      ATTRIBUTES.register("energy_penetration", () -> attribute("energy_penetration"));
  private static final UUID RESISTANCE_ID = UUID.fromString("0912b770-9ac3-4ef4-b18a-8f9fe92a0001");
  private static final UUID PENETRATION_ID =
      UUID.fromString("0912b770-9ac3-4ef4-b18a-8f9fe92a0002");
  private static final UUID BURST_RESISTANCE_ID =
      UUID.fromString("0912b770-9ac3-4ef4-b18a-8f9fe92a0003");
  private static final UUID BURST_PENETRATION_ID =
      UUID.fromString("0912b770-9ac3-4ef4-b18a-8f9fe92a0004");

  public static void syncBurst(Player player, boolean active) {
    if (player.getInventory() == null || player.getAttributes() == null) return;
    syncBurst(player, RESISTANCE, BURST_RESISTANCE_ID, active);
    syncBurst(player, PENETRATION, BURST_PENETRATION_ID, active);
  }

  private static void syncBurst(
      Player player, RegistryObject<Attribute> type, UUID id, boolean active) {
    if (!type.isPresent()) return;
    var instance = player.getAttribute(type.get());
    if (instance == null) return;
    double amount = CombatConfig.burstEnergy(id.equals(BURST_PENETRATION_ID));
    active = active && amount > 0;
    var current = instance.getModifier(id);
    if (current != null
        && (!active
            || current.getAmount() != amount
            || current.getOperation() != AttributeModifier.Operation.ADDITION)) {
      instance.removeModifier(id);
      current = null;
    }
    if (active && current == null) instance.addTransientModifier(modifier(id, amount));
  }

  private static Attribute attribute(String name) {
    return new RangedAttribute("attribute.judgfire." + name, 0, 0, 1.0e12).setSyncable(true);
  }

  public static void register(IEventBus bus) {
    ATTRIBUTES.register(bus);
    bus.addListener(EnergyAttributes::addAttributes);
  }

  private static void addAttributes(EntityAttributeModificationEvent event) {
    for (var type : event.getTypes()) {
      event.add(type, RESISTANCE.get());
      event.add(type, PENETRATION.get());
    }
  }

  public static void itemModifiers(ItemAttributeModifierEvent event) {
    if (event.getSlotType() != EquipmentSlot.MAINHAND) return;
    double resistance = amount(event.getItemStack(), false),
        penetration = amount(event.getItemStack(), true);
    if (resistance > 0) event.addModifier(RESISTANCE.get(), modifier(RESISTANCE_ID, resistance));
    if (penetration > 0)
      event.addModifier(PENETRATION.get(), modifier(PENETRATION_ID, penetration));
  }

  private static double amount(ItemStack stack, boolean penetration) {
    var gun = IGun.getIGunOrNull(stack);
    if (gun == null) return 0;
    var gunId = gun.getGunId(stack);
    var catalog = UpgradeCatalog.builtin();
    if (!catalog.supportsGun(gunId) && !JudgmentFlame.GUN.equals(gunId)) return 0;
    var attachmentId = gun.getAttachmentId(stack, AttachmentType.LASER);
    var definition = catalog.lookup(gunId, attachmentId);
    if (definition != null) return definition.amount(penetration);
    return JudgmentFlame.GUN.equals(gunId)
        ? CombatConfig.attachmentEnergy(attachmentId, penetration)
        : 0;
  }

  private static AttributeModifier modifier(UUID id, double amount) {
    return new AttributeModifier(
        id, "Judgfire energy attachment", amount, AttributeModifier.Operation.ADDITION);
  }

  public static double resistance(LivingEntity entity) {
    return value(entity, RESISTANCE, RESISTANCE_ID, false);
  }

  public static double penetration(LivingEntity entity) {
    return value(entity, PENETRATION, PENETRATION_ID, true);
  }

  private static double value(
      LivingEntity entity, RegistryObject<Attribute> type, UUID id, boolean penetration) {
    // LivingEntity's constructor calls setHealth before Player initializes its inventory.
    // Attribute consumers can still observe an unfinished equipment state.
    if (entity instanceof Player player && player.getInventory() == null) return 0;
    if (entity == null || !type.isPresent() || entity.getAttributes() == null) return 0;
    var original = entity.getAttribute(type.get());
    if (original == null) return 0;
    // Evaluate the actual held stack now: equipment attribute updates can lag a slot/NBT change.
    // Preserve all external base values and modifiers, including multiplicative ones.
    var effective = new AttributeInstance(type.get(), ignored -> {});
    effective.setBaseValue(original.getBaseValue());
    UUID burstId = penetration ? BURST_PENETRATION_ID : BURST_RESISTANCE_ID;
    for (var mod : original.getModifiers())
      if (!mod.getId().equals(id) && !mod.getId().equals(burstId))
        effective.addTransientModifier(mod);
    if (entity instanceof Player) {
      double heldAmount = amount(entity.getMainHandItem(), penetration);
      if (heldAmount > 0) effective.addTransientModifier(modifier(id, heldAmount));
    }
    double bonus = BurstRuntime.energyBonus(entity, penetration);
    if (bonus > 0) effective.addTransientModifier(modifier(burstId, bonus));
    return effective.getValue();
  }
}
