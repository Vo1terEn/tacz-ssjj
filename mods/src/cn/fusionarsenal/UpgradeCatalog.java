package cn.fusionarsenal;

import java.util.*;
import net.minecraft.resources.ResourceLocation;

/**
 * Immutable exact-ID lookup. The built-in table is compiled from config/fusion_upgrades.json;
 * it never reads client files or accepts runtime registrations.
 */
public final class UpgradeCatalog {
  private static final UpgradeCatalog BUILTIN =
      new UpgradeCatalog(GeneratedUpgradeCatalog.definitions());
  private final List<UpgradeDefinition> definitions;
  private final Map<ResourceLocation, Map<ResourceLocation, UpgradeDefinition>> byGun;

  /** Builds an independent immutable catalog, rejecting duplicate gun/attachment pairs. */
  public UpgradeCatalog(Collection<UpgradeDefinition> definitions) {
    this.definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
    var grouped = new HashMap<ResourceLocation, Map<ResourceLocation, UpgradeDefinition>>();
    for (var definition : this.definitions) {
      var attachments = grouped.computeIfAbsent(definition.gunId(), ignored -> new HashMap<>());
      if (attachments.putIfAbsent(definition.attachmentId(), definition) != null)
        throw new IllegalArgumentException(
            "Duplicate upgrade pair: " + definition.gunId() + " / " + definition.attachmentId());
    }
    var frozen = new HashMap<ResourceLocation, Map<ResourceLocation, UpgradeDefinition>>();
    grouped.forEach((gunId, attachments) -> frozen.put(gunId, Map.copyOf(attachments)));
    this.byGun = Map.copyOf(frozen);
  }

  public static UpgradeCatalog builtin() {
    return BUILTIN;
  }

  /** SHA-256 of the exact build-time manifest, for provenance and diagnostics. */
  public static String manifestSha256() {
    return GeneratedUpgradeCatalog.MANIFEST_SHA256;
  }

  public List<UpgradeDefinition> definitions() {
    return definitions;
  }

  public boolean supportsGun(ResourceLocation gunId) {
    return gunId != null && byGun.containsKey(gunId);
  }

  /** Returns null when the exact pair is absent; a present definition may explicitly contain zero. */
  public UpgradeDefinition lookup(ResourceLocation gunId, ResourceLocation attachmentId) {
    if (gunId == null || attachmentId == null) return null;
    var attachments = byGun.get(gunId);
    return attachments == null ? null : attachments.get(attachmentId);
  }

  public double amount(
      ResourceLocation gunId, ResourceLocation attachmentId, boolean penetration) {
    var definition = lookup(gunId, attachmentId);
    return definition == null ? 0 : definition.amount(penetration);
  }
}
