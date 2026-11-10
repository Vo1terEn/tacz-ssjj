package ssjj.dark;

import com.tacz.guns.item.ModernKineticGunItem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Old ssjj_dark:melee saves use TaCZ's gun item behavior and native renderer. */
@Mod.EventBusSubscriber(modid=DarkCrystalMod.ID)
public final class DarkMeleeItem extends ModernKineticGunItem {
    private static final String LEGACY_ID = "MeleeWeaponId";
    private static final ResourceLocation INVALID = new ResourceLocation("ssjj_dark", "invalid");

    public DarkMeleeItem() { super(); }

    @Override
    public ResourceLocation getGunId(ItemStack stack) {
        CompoundTag tag = stack.m_41783_();
        if (tag != null && (tag.m_128441_("GunId") && !tag.m_128425_("GunId", 8)
            || tag.m_128441_(LEGACY_ID) && !tag.m_128425_(LEGACY_ID, 8))) return INVALID;
        String raw = tag == null ? "" : tag.m_128461_("GunId");
        if (raw.isEmpty() && tag != null) raw = tag.m_128461_(LEGACY_ID);
        // Only the legacy item defaults an absent ID to the base grade.
        if (raw.isEmpty()) raw = "ssjj_dark:darkdiamond";
        String id = DarkMeleeRules.canonical(raw);
        return id.isEmpty() ? INVALID : new ResourceLocation(id);
    }

    public ResourceLocation getId(ItemStack stack) { return getGunId(stack); }

    public void setId(ItemStack stack, ResourceLocation id) {
        String canonical = id == null ? "" : DarkMeleeRules.canonical(id.toString());
        if (canonical.isEmpty()) throw new IllegalArgumentException("Unknown dark crystal weapon: " + id);
        setGunId(stack, new ResourceLocation(canonical));
        setFireMode(stack, FireMode.AUTO);
        stack.m_41784_().m_128359_(LEGACY_ID, canonical);
    }

    public static String canonicalId(ItemStack stack) { return DarkCombat.weaponId(stack); }

    @Override
    public void m_6883_(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        super.m_6883_(stack, level, entity, slot, selected);
        if (level.f_46443_) return;
        String id = DarkMeleeRules.canonical(getGunId(stack).toString());
        if (!id.isEmpty()) {
            CompoundTag tag = stack.m_41784_();
            if (!id.equals(tag.m_128461_("GunId"))) setGunId(stack, new ResourceLocation(id));
            if (!id.equals(tag.m_128461_(LEGACY_ID))) tag.m_128359_(LEGACY_ID, id);
            // Convert old inventory stacks to the standard pistol item so tier upgrades remain craftable.
            if (entity instanceof net.minecraft.world.entity.player.Player player
                && player.m_150109_().m_8020_(slot) == stack) {
                var item=net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                    new ResourceLocation("tacz","modern_kinetic_gun"));
                if (item != null) {
                    ItemStack converted=new ItemStack(item,stack.m_41613_());
                    converted.m_41751_(tag.m_6426_());
                    converted.m_41784_().m_128473_(LEGACY_ID);
                    IGun.getIGunOrNull(converted).setFireMode(converted, FireMode.AUTO);
                    player.m_150109_().m_6836_(slot,converted);
                }
            }
        }
    }

    /** Existing standard TaCZ stacks also migrate from the former SEMI default. */
    @SubscribeEvent
    public static void automaticMode(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        var inventory = player.m_150109_();
        for (int slot = 0; slot < inventory.m_6643_(); slot++) {
            syncAutoMode(inventory.m_8020_(slot));
        }
    }

    /** Guard using raw, read-only NBT before TaCZ accessors may create a tag. */
    static void syncAutoMode(ItemStack stack) {
        if (stack == null || stack.m_41619_()) return;
        CompoundTag tag = stack.m_41783_();
        if (tag == null || !tag.m_128425_("GunId", 8)) return;
        String id = DarkMeleeRules.canonical(tag.m_128461_("GunId"));
        if (!DarkHeldInput.isDarkWeapon(id)) return;
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun != null && gun.getFireMode(stack) != FireMode.AUTO) gun.setFireMode(stack, FireMode.AUTO);
    }

    @Override
    public void m_7373_(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.m_7373_(stack, level, tooltip, flag);
        String id = canonicalId(stack);
        int grade = DarkMeleeRules.grade(id);
        if (grade < 0) return;
        tooltip.add(Component.m_237113_("旧版黯水晶已兼容为手枪类别；左右键挥砍"));
        tooltip.add(Component.m_237113_("近战伤害 " + (7 + grade) + " · 重击 ×1.65"));
    }
}
