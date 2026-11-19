package cn.ssjjcompat;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;

/** Shared category and creation entry point for the addon's client key mappings. */
public final class ArsenalKeyBindings {
    public static final String CATEGORY = "key.categories.ssjjrpg";

    private ArsenalKeyBindings() {}

    /** Retains KeyMapping's default conflict context, including saved binding IDs. */
    public static KeyMapping create(String translationKey, InputConstants.Type type, int keyCode) {
        return new KeyMapping(translationKey, type, keyCode, CATEGORY);
    }

    /** Uses the caller's existing context; weapon eligibility stays in its input handler. */
    public static KeyMapping create(String translationKey, IKeyConflictContext context,
                                    InputConstants.Type type, int keyCode) {
        return new KeyMapping(translationKey, context, type, keyCode, CATEGORY);
    }

    /** Call from the owning module's client MOD-bus registration event. */
    public static void register(RegisterKeyMappingsEvent event, KeyMapping mapping) {
        event.register(mapping);
    }
}
