package cn.ssjjares.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Keep vanilla armor unless a matching complete set is actually using Ares YSM. */
@OnlyIn(Dist.CLIENT)
public final class AresInvisibleArmor implements IClientItemExtensions {
    public static final AresInvisibleArmor INSTANCE=new AresInvisibleArmor();
    private HumanoidModel<?> model;
    private AresInvisibleArmor(){}
    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original){
        // This shared gate checks complete same-tier armor, optional YSM presence,
        // capability enabled, loaded native model and its exact selected model ID.
        if(!(entity instanceof AbstractClientPlayer player)||AresWeightedHands.modelTier(player)==0)return original;
        if(model==null){
            var mesh=new MeshDefinition();var root=mesh.getRoot();
            for(var name:new String[]{"head","hat","body","left_arm","right_arm","left_leg","right_leg"})root.addOrReplaceChild(name,CubeListBuilder.create(),net.minecraft.client.model.geom.PartPose.ZERO);
            model=new HumanoidModel<>(LayerDefinition.create(mesh,16,16).bakeRoot());
        }
        return model;
    }
}
