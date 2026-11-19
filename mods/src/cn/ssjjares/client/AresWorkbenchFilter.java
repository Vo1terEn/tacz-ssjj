package cn.ssjjares.client;

import cn.ssjjares.AresArmorItem;
import cn.ssjjares.AresAccessoryItem;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The armor page remains available alongside a held gun's ammo/attachment filtering. */
@OnlyIn(Dist.CLIENT)
public final class AresWorkbenchFilter {
    private static final ResourceLocation WORKBENCH=new ResourceLocation("ssjj_workbench:rongguang"),GROUP=new ResourceLocation("ssjj_workbench:armor");
    private AresWorkbenchFilter(){}
    public static boolean accepts(GunSmithTableScreen screen,GunSmithTableRecipe recipe){
        return screen.getMenu()!=null&&WORKBENCH.equals(screen.getMenu().getBlockId())
            &&recipe!=null&&recipe.getId().getNamespace().equals("ssjj_ares")&&GROUP.equals(recipe.getResult().getGroup())
            &&(recipe.getOutput().getItem() instanceof AresArmorItem||recipe.getOutput().getItem() instanceof AresAccessoryItem);
    }
}
