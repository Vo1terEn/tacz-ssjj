package cn.fusionarsenal;

import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** One immutable energy upgrade for an exact gun ID and its installed native LASER attachment ID. */
public record UpgradeDefinition(
    ResourceLocation gunId, ResourceLocation attachmentId, double resistance, double penetration) {
  public UpgradeDefinition {
    Objects.requireNonNull(gunId, "gunId");
    Objects.requireNonNull(attachmentId, "attachmentId");
    if (gunId.getPath().isEmpty() || attachmentId.getPath().isEmpty())
      throw new IllegalArgumentException("Upgrade IDs must have nonempty paths");
    if (!Double.isFinite(resistance) || resistance < 0
        || !Double.isFinite(penetration) || penetration < 0)
      throw new IllegalArgumentException("Energy amounts must be finite and nonnegative");
  }

  public double amount(boolean usePenetration) {
    return usePenetration ? penetration : resistance;
  }
}
