package ssjj.dark;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** A scope selects presentation only: the native GunId and combat tier never change. */
@Mod.EventBusSubscriber(modid = DarkCrystalMod.ID)
public final class DarkSkins {
    private static final String[] GRADES = { "darkdiamond", "darkdiamond1", "darkdiamond2" };
    private static final String[] SUFFIXES = { "", "_mix", "_nightguardian", "_pure" };
    private static final ResourceLocation[][] DISPLAYS = new ResourceLocation[3][4];
    private static final ResourceLocation[][] TEXTURES = new ResourceLocation[3][4];
    static {
        for (int grade = 0; grade < GRADES.length; grade++) {
            for (int skin = 0; skin < SUFFIXES.length; skin++) {
                String name = GRADES[grade] + SUFFIXES[skin];
                DISPLAYS[grade][skin] = new ResourceLocation(DarkCrystalMod.ID, name + "_display");
                TEXTURES[grade][skin] = new ResourceLocation(DarkCrystalMod.ID, "textures/thirdperson/" + name + ".png");
            }
        }
    }

    private static int skin(IGun gun, ItemStack stack) {
        ResourceLocation scope = gun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (scope == null || !DarkCrystalMod.ID.equals(scope.m_135827_())) return 0;
        return switch (scope.m_135815_()) {
            case "skin_mix" -> 1;
            case "skin_nightguardian" -> 2;
            case "skin_pure" -> 3;
            default -> 0;
        };
    }

    /** Also used by the original-UV skill avatar, independent of the native atlas. */
    static ResourceLocation bladeTexture(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        int grade = grade(gun, stack);
        return grade < 0 || gun == null ? TEXTURES[0][0] : TEXTURES[grade][skin(gun, stack)];
    }

    private static int grade(IGun gun, ItemStack stack) {
        if (gun == null) return -1;
        var tag = stack.m_41783_();
        // Native getGunId uses getOrCreateTag. Do not create NBT on an empty
        // or malformed gun, and never parse untrusted IDs as ResourceLocations.
        if (tag == null || !tag.m_128425_("GunId", 8)) return -1;
        return DarkMeleeRules.grade(DarkMeleeRules.canonical(tag.m_128461_("GunId")));
    }

    static void sync(ItemStack stack) {
        IGun gun = IGun.getIGunOrNull(stack);
        int grade = grade(gun, stack);
        // Unknown and unrelated guns are never assigned a display by this adapter.
        if (grade < 0) return;
        ResourceLocation display = DISPLAYS[grade][skin(gun, stack)];
        if (!display.equals(gun.getGunDisplayId(stack))) gun.setGunDisplayId(stack, display);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.m_9236_().f_46443_) return;
        var inventory = event.player.m_150109_();
        for (int slot = 0; slot < inventory.m_6643_(); slot++) {
            sync(inventory.m_8020_(slot));
        }
    }

    private DarkSkins() {}
}
