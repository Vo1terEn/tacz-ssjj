package cn.ssjjdark;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;

/** One native magazine cell is a permanently installed crystal, never a melee cost. */
public final class DarkCore {
    public static final String SCHEMA="ssjj_dark_core_schema";
    private DarkCore() {}
    public static boolean ready(ItemStack stack) {
        return DarkCombat.matches(stack)&&stack.getTag().getInt(SCHEMA)==1
            &&((IGun)stack.getItem()).getCurrentAmmoCount(stack)>0;
    }
    /** Server only: retire the former 9mm placeholder before accepting the new core. */
    public static void prepare(ItemStack stack) {
        if(!DarkCombat.matches(stack))return;
        var gun=(IGun)stack.getItem();var tag=stack.getOrCreateTag();
        if(tag.getInt(SCHEMA)!=1) {
            gun.setCurrentAmmoCount(stack,0);gun.setBulletInBarrel(stack,false);
            tag.putInt(SCHEMA,1);
        } else {
            // A crystal has one cell and no separate chamber. Never refill an empty cell.
            if(gun.getCurrentAmmoCount(stack)>1)gun.setCurrentAmmoCount(stack,1);
            if(gun.hasBulletInBarrel(stack))gun.setBulletInBarrel(stack,false);
        }
    }
}
