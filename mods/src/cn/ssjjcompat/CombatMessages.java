package cn.ssjjcompat;

import cn.ssjjares.AresMissileDamage;
import cn.ssjjparasite2.client.ParasiteDestructionDamage;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.GunDamageSourcePart;
import com.tacz.guns.entity.EntityKineticBullet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/** The Dimension Star death message layout, with a palette for each weapon family. */
@Mod.EventBusSubscriber(modid = "eye_judgment_arsenal")
public final class CombatMessages {
    private static final Map<DamageSource, String> STYLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ResourceLocation, String> SHOTS = gunStyles();
    private static final GunDamageSourcePart[] GUN_PARTS = GunDamageSourcePart.values();
    private static final String PROJECTILE_STYLE = "ssjjrpg_death_style";
    private static final int VICTIM_COLOR = 0xE5DCF3;
    private record Palette(int accent, int text) {}
    private static final Palette DIMENSION = new Palette(0x67DDFF, 0xB9B3EC);
    private static final Palette PARASITE = new Palette(0x92DF57, 0xB5CCA6);
    private static final Palette EYE = new Palette(0xB68CFF, 0xCBC2EF);
    private static final Palette FLAME = new Palette(0xFF9E58, 0xF6C8AE);
    private static final Palette ZEUS = new Palette(0x75C9FF, 0xB9D8F4);
    private static final Palette HYPER = new Palette(0xFFA35C, 0xE9D4BE);
    private static final Palette DARK = new Palette(0xE184FC, 0xD8BCE8);
    private static final Palette ARES = new Palette(0xFC6D5F, 0xE9BCB5);

    public CombatMessages() {}

    private static Map<ResourceLocation, String> gunStyles() {
        var entries = new HashMap<ResourceLocation, String>();
        guns(entries, "eye_shot", "eyeofpunishment", "notdistinguish_base", "notdistinguish_tier1", "notdistinguish");
        guns(entries, "flame_shot", "judgfire", "judgment_flame_base", "judgment_flame_tier1", "judgment_flame");
        guns(entries, "dimension_shot", "ssjj_dimension", "dimension_star", "dimension_star1", "dimension_star2");
        guns(entries, "hyper_shot", "ssjj_hyper", "hyper_revolver", "hyper_revolver1", "hyper_revolver2");
        guns(entries, "zeus_shot", "ssjj_zeus", "hand_of_zeuss", "hand_of_zeuss1", "hand_of_zeuss2");
        guns(entries, "parasite_shot", "ssjjparasite2", "parasitic_sniper", "parasitic_sniper1", "parasitic_sniper2");
        guns(entries, "dark_slash", "ssjj_dark", "darkdiamond", "darkdiamond1", "darkdiamond2");
        return Map.copyOf(entries);
    }

    private static void guns(Map<ResourceLocation, String> entries, String style, String namespace, String... paths) {
        for (String path : paths) entries.put(new ResourceLocation(namespace, path), style);
    }

    public static DamageSource remember(DamageSource source, String style) {
        STYLES.put(source, style);
        return source;
    }

    public static void rememberProjectile(Entity projectile, String style) {
        projectile.getPersistentData().putString(PROJECTILE_STYLE, style);
    }

    public static void rememberProjectileIfAbsent(Entity projectile, String style) {
        if (!projectile.getPersistentData().contains(PROJECTILE_STYLE)) rememberProjectile(projectile, style);
    }

    static String shotStyle(ResourceLocation id, Entity bullet) {
        String style = id == null ? null : SHOTS.get(id);
        if (style == null || bullet == null) return style;
        return switch (style) {
            case "hyper_shot" -> bullet.getPersistentData().getBoolean("ssjj_hyper_ray") ? "hyper_ray" : style;
            case "parasite_shot" -> bullet.getPersistentData().getBoolean("ssjjparasite2_cannon_shot")
                ? "parasite_wave" : style;
            case "zeus_shot" -> switch (bullet.getPersistentData().getString(PROJECTILE_STYLE)) {
                case "zeus_burst" -> "zeus_burst";
                case "zeus_chain" -> "zeus_chain";
                default -> style;
            };
            default -> style;
        };
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void bullet(EntityHurtByGunEvent.Pre event) {
        if (event.getLogicalSide() != LogicalSide.SERVER) return;
        String style = shotStyle(event.getGunId(), event.getBullet());
        if (style == null) return;
        for (var part : GUN_PARTS) {
            var source = event.getDamageSource(part);
            if (source != null) remember(source, style);
        }
    }

    public static Component message(DamageSource source, LivingEntity victim) {
        Entity direct = source.getDirectEntity();
        String style;
        if (source instanceof ParasiteDestructionDamage) style = "parasite_destruction";
        else if (source instanceof AresMissileDamage) style = "ares_missile";
        else {
            style = STYLES.get(source);
            if (style == null && direct instanceof EntityKineticBullet bullet)
                style = shotStyle(bullet.getGunId(), bullet);
        }
        if (style == null) return null;
        Entity attacker = source.getEntity();
        if (attacker == null && direct instanceof EntityKineticBullet bullet) attacker = bullet.getOwner();
        if (attacker == null) return null;
        return format(style, victim.getDisplayName(), attacker.getDisplayName());
    }

    public static Component eye(Component victim, Component attacker, boolean cloud) {
        return format(cloud ? "eye_cloud" : "eye_lightning", victim, attacker);
    }

    public static Component flame(Component victim, Component attacker, Component weapon, boolean crown) {
        return format(crown ? "flame_crown" : "flame_shot", victim, attacker, weapon);
    }

    public static String darkStrike(boolean heavy) {
        return heavy ? "dark_heavy" : "dark_slash";
    }

    public static Component format(String style, Component victim, Component attacker) {
        Component weapon = "flame_shot".equals(style) ? Component.translatable("death.ssjj.flame_weapon") : null;
        return format(style, victim, attacker, weapon);
    }

    private static Component format(String style, Component victim, Component attacker, Component weapon) {
        var palette = palette(style);
        String key = "death.ssjj." + style;
        var prefix = Component.translatable(key + ".prefix")
            .withStyle(s -> s.withColor(palette.accent()).withBold(true));
        var victimName = victim.copy().withStyle(s -> s.withColor(VICTIM_COLOR));
        var attackerName = attacker.copy().withStyle(s -> s.withColor(palette.accent()));
        Object[] names = weapon == null ? new Object[] {victimName, attackerName}
            : new Object[] {victimName, attackerName, weapon.copy().withStyle(s -> s.withColor(palette.accent()))};
        var line = Component.translatable(key, names).withStyle(s -> s.withColor(palette.text()).withBold(false));
        return prefix.append(line);
    }

    private static Palette palette(String style) {
        return switch (style) {
            case "parasite_shot", "parasite_wave", "parasite_destruction" -> PARASITE;
            case "eye_shot", "eye_lightning", "eye_cloud" -> EYE;
            case "flame_shot", "flame_crown" -> FLAME;
            case "zeus_shot", "zeus_burst", "zeus_chain" -> ZEUS;
            case "hyper_shot", "hyper_ray", "hyper_charge" -> HYPER;
            case "dark_slash", "dark_heavy", "dark_dragon", "dark_liberation" -> DARK;
            case "ares_missile" -> ARES;
            default -> DIMENSION;
        };
    }
}
