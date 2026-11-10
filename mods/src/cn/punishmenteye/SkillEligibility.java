package cn.punishmenteye;
import cn.fusionarsenal.config.ProgressionConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import com.tacz.guns.api.item.IGun;
/** Actual stock ownership remains distinct from innate second-tier eligibility. */
public final class SkillEligibility {
 public static boolean isSecond(ResourceLocation id){return id!=null&&id.getNamespace().equals("eyeofpunishment")&&id.getPath().equals("notdistinguish");}
 public static boolean isFirst(ResourceLocation id){return id!=null&&id.getNamespace().equals("eyeofpunishment")&&id.getPath().equals("notdistinguish_tier1");}
 public static boolean eligible(ItemStack stack){var gun=IGun.getIGunOrNull(stack);return ProgressionConfig.enabled("eye.king_enabled")&&gun!=null&&isSecond(gun.getGunId(stack));}
 public static int duration(ItemStack stack){return TimerRules.duration(StockRequirement.installed(stack));}
 public static int cooldown(ItemStack stack){return TimerRules.cooldown(StockRequirement.installed(stack));}
}
