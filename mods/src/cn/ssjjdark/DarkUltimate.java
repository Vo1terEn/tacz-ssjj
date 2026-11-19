package cn.ssjjdark;

import cn.fusionarsenal.api.UltimateStoneRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/** The shared registry also grants the registered native skill to a legally installed Creative Stone. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class DarkUltimate {
    public static final ResourceLocation ID=new ResourceLocation("ssjj_dark","ultimate_upgrade_stone");
    public static final ResourceLocation GUN=new ResourceLocation("ssjj_dark","darkdiamond2");
    private DarkUltimate(){}
    public static void register(){UltimateStoneRegistry.register(GUN,ID);}
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){register();}
    public static boolean installed(ItemStack stack){
        if(DarkWrath.tier(stack)!=2)return false;
        register();
        return UltimateStoneRegistry.installed(stack,ID);
    }
}
