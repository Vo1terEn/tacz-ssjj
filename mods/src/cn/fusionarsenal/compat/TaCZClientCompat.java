package cn.fusionarsenal.compat;

import com.tacz.guns.api.client.other.KeepingItemRenderer;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.ClientIndexManager;
import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.function.Consumer;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Shared client integration boundary for every arsenal weapon. No version strings or weapon rules. */
public final class TaCZClientCompat {
    private static final MethodHandle MODEL_WARMUP = optionalWarmup("warmUpModel");
    private static final MethodHandle RUNTIME_WARMUP = optionalWarmup("warmUpRuntime");
    private static final Set<String> PACK_NAMESPACES = Set.of("eyeofpunishment", "judgfire", "ssjj_dark", "ssjj_dimension", "ssjjparasite2", "ssjjrpg", "ssjj_zeus", "ssjj_workbench");
    private static volatile Set<ResourceLocation> hiddenMountDisplays = Set.of();
    private TaCZClientCompat() {}

    /**
     * Bind a display's geometry to every native instance that can actually draw it.
     * Older TaCZ releases create a distinct default instance for each gun index;
     * modern releases reuse the named display instance. Neither API shape reveals
     * that identity difference, so callers must not bind only the named instance.
     */
    public static void forEachGunDisplay(ResourceLocation displayId, ResourceLocation gunId,
                                         Consumer<GunDisplayInstance> action) {
        Objects.requireNonNull(displayId, "displayId");
        Objects.requireNonNull(gunId, "gunId");
        Objects.requireNonNull(action, "action");
        GunDisplayInstance named = null;
        // TimelessAPI falls back to a gun's default when a named display is absent.
        // A missing optional skin must never install its geometry on that default.
        if (ClientAssetsManager.INSTANCE.getGunDisplay(displayId) != null) {
            // Follow installed native lookup; consume exact named cache key,
            // never fallback.
            TimelessAPI.getGunDisplay(displayId, gunId);
            named = ClientIndexManager.GUN_DISPLAY.get(displayId);
            if (named != null) action.accept(named);
        }
        var common = TimelessAPI.getCommonGunIndex(gunId).orElse(null);
        if (common == null || common.getPojo() == null
            || !displayId.equals(common.getPojo().getDisplay())) return;
        var client = TimelessAPI.getClientGunIndex(gunId).orElse(null);
        GunDisplayInstance actualDefault = client == null ? null : client.getDefaultDisplay();
        if (actualDefault != null && actualDefault != named) action.accept(actualDefault);
    }

    private static MethodHandle optionalWarmup(String name) {
        try {
            return MethodHandles.publicLookup().unreflect(GunDisplayInstance.class.getMethod(name));
        } catch (NoSuchMethodException eagerAssetLoader) {
            // TaCZ 1.1.5-1.1.7 initialize the model and animation in the display constructor.
            return null;
        } catch (IllegalAccessException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void warmup(MethodHandle method, GunDisplayInstance display) {
        if (method == null) return;
        try { method.invokeExact(display); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot prepare TaCZ assets", failure); }
    }

    /** Preserve native asynchronous warm-up on lazy releases; eager releases need no extra work. */
    public static void warmModel(GunDisplayInstance display) { warmup(MODEL_WARMUP, display); }
    public static void warmRuntime(GunDisplayInstance display) { warmup(RUNTIME_WARMUP, display); }

    /** Finish native CPU preparation during resource reload without advancing animation state. */
    public static BedrockGunModel prepareDisplay(GunDisplayInstance display) {
        BedrockGunModel model = display.getGunModel();
        if (model != null) display.getAnimationStateMachine();
        return model;
    }

    /** Re-read effective resource-pack overrides after the native client index reload. */
    public static void onResourceReload() {
        onResourceReload(Minecraft.m_91087_().m_91098_());
    }

    /** Explicit resource boundary shared by the reload coordinator and independent pack validation. */
    public static void onResourceReload(ResourceManager resourceManager) {
        Set<ResourceLocation> hidden = new HashSet<>();
        var resources = resourceManager.m_214159_("display/attachments",
            id -> PACK_NAMESPACES.contains(id.m_135827_()) && id.m_135815_().endsWith(".json"));
        for (var entry : resources.entrySet()) {
            try (var reader = entry.getValue().m_215508_()) {
                var root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("show_mount") && !root.get("show_mount").getAsBoolean()) hidden.add(entry.getKey());
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot read arsenal attachment display " + entry.getKey(), failure);
            }
        }
        hiddenMountDisplays = Set.copyOf(hidden);
    }

    /** Backport only the missing mount flag; keep the native scope-presence predicate authoritative. */
    public static boolean mountVisible(boolean nativeVisible, ItemStack scopeStack) {
        if (!nativeVisible) return false;
        // Native BedrockGunModel supplies currentAttachmentItem[SCOPE], not the gun stack.
        var attachment = IAttachment.getIAttachmentOrNull(scopeStack);
        if (attachment == null) return nativeVisible;
        var scope = attachment.getAttachmentId(scopeStack);
        if (scope == null || !PACK_NAMESPACES.contains(scope.m_135827_())) return nativeVisible;
        var index = TimelessAPI.getCommonAttachmentIndex(scope).orElse(null);
        // A client-only resource reload can precede server common-index synchronization.
        if (index == null) return nativeVisible;
        var display = index.getPojo().getDisplay();
        if (display == null) return nativeVisible;
        var resource = new ResourceLocation(display.m_135827_(), "display/attachments/" + display.m_135815_() + ".json");
        return !hiddenMountDisplays.contains(resource);
    }

    /** Original TaCZ retained renderer, including outgoing-item transitions. */
    public static KeepingItemRenderer retainedRenderer() { return KeepingItemRenderer.getRenderer(); }
    public static ItemStack retainedItem(KeepingItemRenderer renderer) { return renderer.getCurrentItem(); }

    private static final class DrawnItem {
        static final MethodHandle ACTIVE;
        static final MethodHandle ITEM;
        static {
            MethodHandle active = null, item = null;
            try {
                ClassLoader loader = TaCZClientCompat.class.getClassLoader();
                Class<?> handler = Class.forName("com.github.mcmodderanchor.simplebedrockmodel.v1.client.handler.FirstPersonRenderHandler", false, loader);
                Class<?> instance = Class.forName("com.github.mcmodderanchor.simplebedrockmodel.v1.client.animation.IFPAnimationInstance", false, loader);
                var lookup = MethodHandles.publicLookup();
                active = lookup.unreflect(handler.getMethod("getActiveAnimationInstance"))
                    .asType(MethodType.methodType(Object.class));
                item = lookup.unreflect(instance.getMethod("currentItem"))
                    .asType(MethodType.methodType(ItemStack.class, Object.class));
            } catch (ClassNotFoundException legacyRenderer) {
                // The older native renderer exposes the same drawn stack through KeepingItemRenderer.
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
            ACTIVE = active; ITEM = item;
        }
    }

    /** Item selected by the installed drawing implementation, rather than the player's live hand. */
    public static ItemStack currentRenderedItem() {
        if (DrawnItem.ACTIVE == null) {
            KeepingItemRenderer renderer = retainedRenderer();
            return renderer == null ? null : retainedItem(renderer);
        }
        try {
            Object active = (Object) DrawnItem.ACTIVE.invokeExact();
            return active == null ? null : (ItemStack) DrawnItem.ITEM.invokeExact(active);
        } catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot obtain TaCZ drawn item", failure); }
    }

    private static final class Sounds {
        static final MethodHandle GET;
        static final MethodHandle PLAY;
        static {
            try {
                var lookup = MethodHandles.publicLookup();
                var sounds = GunDisplayInstance.class.getMethod("getSounds", String.class);
                GET = lookup.unreflect(sounds).asType(MethodType.methodType(Object.class, GunDisplayInstance.class, String.class));
                var play = SoundPlayManager.class.getMethod("playClientSound", Entity.class, sounds.getReturnType(), float.class, float.class, int.class);
                PLAY = lookup.unreflect(play).asType(MethodType.methodType(GunSoundInstance.class, Entity.class, Object.class, float.class, float.class, int.class));
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    /** TaCZ changed the native sound value type in 1.2; keep each release's own resolution/playback. */
    public static Object getSounds(GunDisplayInstance display, String key) {
        try { return (Object) Sounds.GET.invokeExact(display, key); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot resolve TaCZ sound", failure); }
    }

    public static GunSoundInstance playClientSound(Entity entity, Object sound, float volume, float pitch, int distance) {
        try { return (GunSoundInstance) Sounds.PLAY.invokeExact(entity, sound, volume, pitch, distance); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Throwable failure) { throw new IllegalStateException("Cannot play TaCZ sound", failure); }
    }
}
