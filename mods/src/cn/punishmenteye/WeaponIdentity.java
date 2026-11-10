package cn.punishmenteye;
import net.minecraft.resources.ResourceLocation;
/** Cosmetic attachments never change the gun's gameplay identity. */
public final class WeaponIdentity {
 public static boolean matches(ResourceLocation id){return GripRequirement.WEAPON.equals(id);}
}
