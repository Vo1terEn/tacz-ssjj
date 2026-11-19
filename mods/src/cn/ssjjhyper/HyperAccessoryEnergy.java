package cn.ssjjhyper;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Adds one native energy-stat contribution for each real non-cosmetic Hyper accessory. */
public final class HyperAccessoryEnergy {
    private HyperAccessoryEnergy(){}
    public static int count(ItemStack stack){
        var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);
        if(gun==null||HyperCharge.tier(stack)<0)return 0;
        int count=0;
        for(var slot:List.of(AttachmentType.MUZZLE,AttachmentType.GRIP,AttachmentType.STOCK,AttachmentType.EXTENDED_MAG)){
            if(!gun.allowAttachmentType(stack,slot)||!HyperConfig.enabled(switch(slot){case MUZZLE->"muzzle_enabled";case GRIP->"grip_enabled";case STOCK->"stock_enabled";default->"magazine_enabled";}))continue;
            var id=gun.getAttachmentId(stack,slot);
            if(id==null||!"ssjj_hyper".equals(id.getNamespace())||!TimelessAPI.getCommonAttachmentIndex(id).map(index->index.getType()==slot).orElse(false)
                ||!AllowAttachmentTagMatcher.match(gun.getGunId(stack),id))continue;
            count++;
        }
        return count;
    }
    public static double resistanceOrPenetration(ItemStack stack,boolean penetration){return count(stack)*HyperConfig.number(penetration?"accessory_penetration":"accessory_resistance");}
    public static double strength(ItemStack stack){return count(stack)*HyperConfig.number("accessory_strength");}
}
