package cn.ssjjares.ysm;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Reversible selection with a backup in the player's Forge data. */
public final class AresYsmSelection {
    public static final String BACKUP_KEY = "ssjj_ares:ysm_restore";
    public static final String BASE = "ssjj_ares_base", ABSOLUTE = "ssjj_ares_absolute";
    private static final String ARES_TEXTURE = "main";

    private AresYsmSelection() {}

    public interface ModelAccess {
        String model() throws ReflectiveOperationException;
        String texture() throws ReflectiveOperationException;
        boolean disabled() throws ReflectiveOperationException;
        boolean mandatory() throws ReflectiveOperationException;
        void select(String model, String texture) throws ReflectiveOperationException;
        void disabled(boolean value) throws ReflectiveOperationException;
        void mandatory(boolean value) throws ReflectiveOperationException;
    }

    public static boolean isAres(String id) {
        return BASE.equals(id) || ABSOLUTE.equals(id);
    }

    public static String modelForTier(int tier) {
        return switch (tier) {
            case 1 -> BASE;
            case 2 -> ABSOLUTE;
            default -> "";
        };
    }

    public static void reconcile(int tier, CompoundTag persistent, ModelAccess access, boolean ready,
                                 boolean enabled) throws ReflectiveOperationException {
        if (enabled) reconcile(tier, persistent, access, ready);
        else if (persistent.contains(BACKUP_KEY, Tag.TAG_COMPOUND)) restore(persistent, access);
    }

    public static void reconcile(int tier, CompoundTag persistent, ModelAccess access, boolean ready)
        throws ReflectiveOperationException {
        String wanted = modelForTier(tier);
        if (!ready || wanted.isEmpty()) {
            restore(persistent, access);
            return;
        }

        String current = access.model();
        var backup = persistent.getCompound(BACKUP_KEY);
        if (!valid(backup)) {
            backup = isAres(current) ? fallback()
                : snapshot(current, access.texture(), access.disabled(), access.mandatory());
            persistent.put(BACKUP_KEY, backup);
            if (!valid(backup)) return;
        }
        if (!wanted.equals(current) || !ARES_TEXTURE.equals(access.texture()))
            access.select(wanted, ARES_TEXTURE);
        if (access.disabled()) access.disabled(false);
        // YSM honors this flag when synchronizing the selection to tracking players.
        if (!access.mandatory()) access.mandatory(true);
    }

    public static void restore(CompoundTag persistent, ModelAccess access) throws ReflectiveOperationException {
        boolean saved = persistent.contains(BACKUP_KEY, Tag.TAG_COMPOUND);
        var backup = saved ? persistent.getCompound(BACKUP_KEY) : null;
        if (backup == null || !valid(backup)) {
            if (!isAres(access.model())) {
                if (saved) persistent.remove(BACKUP_KEY);
                return;
            }
            backup = fallback();
        }
        access.select(backup.getString("model"), backup.getString("texture"));
        access.disabled(backup.getBoolean("disabled"));
        access.mandatory(backup.getBoolean("mandatory"));
        persistent.remove(BACKUP_KEY);
    }

    private static boolean valid(CompoundTag backup) {
        if (backup.getInt("schema") != 1 || !backup.contains("model", Tag.TAG_STRING)
            || !backup.contains("texture", Tag.TAG_STRING)) return false;
        String model = backup.getString("model");
        return !model.isEmpty() && !isAres(model);
    }

    private static CompoundTag fallback() {
        return snapshot("default", "default", true, false);
    }

    private static CompoundTag snapshot(String model, String texture, boolean disabled, boolean mandatory) {
        var backup = new CompoundTag();
        backup.putInt("schema", 1);
        backup.putString("model", model);
        backup.putString("texture", texture);
        backup.putBoolean("disabled", disabled);
        backup.putBoolean("mandatory", mandatory);
        return backup;
    }
}
