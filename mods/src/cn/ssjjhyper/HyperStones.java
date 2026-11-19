package cn.ssjjhyper;

import cn.fusionarsenal.api.UltimateStoneRegistry;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.util.AllowAttachmentTagMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** Native LASER selectors; fusion and the existing creative stone grant both traits. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class HyperStones {
    public static final ResourceLocation ULTIMATE=new ResourceLocation("ssjj_hyper","ultimate_upgrade_stone");
    public static final ResourceLocation ABILITY=new ResourceLocation("ssjj_hyper","ability_upgrade_stone");
    public static final ResourceLocation FUSION=new ResourceLocation("ssjj_hyper","fusion_upgrade_stone");
    private HyperStones(){}
    public static void register(){
        UltimateStoneRegistry.register(new ResourceLocation("ssjj_hyper","hyper_revolver2"),ULTIMATE);
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){register();}
    private static ResourceLocation installed(ItemStack stack){
        var gun=stack==null||stack.isEmpty()?null:IGun.getIGunOrNull(stack);
        if(gun==null||HyperCharge.tier(stack)!=2||!gun.allowAttachmentType(stack,AttachmentType.LASER))return null;
        var id=gun.getAttachmentId(stack,AttachmentType.LASER);
        if(id==null||!AllowAttachmentTagMatcher.match(gun.getGunId(stack),id))return null;
        if(ULTIMATE.equals(id)&&!HyperConfig.enabled("ultimate_stone_enabled")||ABILITY.equals(id)&&!HyperConfig.enabled("ability_stone_enabled")||FUSION.equals(id)&&!HyperConfig.enabled("fusion_stone_enabled")||UltimateStoneRegistry.CREATIVE.equals(id)&&!HyperConfig.enabled("creative_stone_enabled"))return null;
        return id;
    }
    public static boolean fusion(ItemStack stack){var id=installed(stack);return FUSION.equals(id)||UltimateStoneRegistry.CREATIVE.equals(id);}
    public static boolean ultimate(ItemStack stack){var id=installed(stack);return ULTIMATE.equals(id)||FUSION.equals(id)||UltimateStoneRegistry.CREATIVE.equals(id);}
    public static boolean ability(ItemStack stack){var id=installed(stack);return ABILITY.equals(id)||FUSION.equals(id)||UltimateStoneRegistry.CREATIVE.equals(id);}
}
