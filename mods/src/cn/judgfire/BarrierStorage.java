package cn.judgfire;

import java.lang.reflect.Field;
import net.minecraft.network.syncher.SynchedEntityData;

/** Exact vanilla storage fields only; no foreign pool discovery or mod-specific fields. */
public final class BarrierStorage {
  private static final Field VALUE = field(SynchedEntityData.DataItem.class, "value", "f_135391_");
  private static final Field ITEM_DIRTY =
      field(SynchedEntityData.DataItem.class, "dirty", "f_135392_");
  private static final Field DATA_DIRTY = field(SynchedEntityData.class, "isDirty", "f_135348_");

  private static Field field(Class<?> type, String... names) {
    for (String name : names)
      try {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
      } catch (ReflectiveOperationException ignored) {
      }
    throw new IllegalStateException("Unsupported vanilla health storage: " + type.getName());
  }

  public static boolean write(SynchedEntityData data, Object item, float value) {
    try {
      VALUE.set(item, value);
      ITEM_DIRTY.setBoolean(item, true);
      DATA_DIRTY.setBoolean(data, true);
      return true;
    } catch (IllegalAccessException e) {
      throw new IllegalStateException("Cannot synchronize protected health", e);
    }
  }

  private BarrierStorage() {}
}
