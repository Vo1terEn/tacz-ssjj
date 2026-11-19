package cn.ssjjares.client;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Only the six new native accessory items use this extension; old armor is unchanged. */
public final class AresAccessoryArmor implements IClientItemExtensions {
    public static final AresAccessoryArmor INSTANCE=new AresAccessoryArmor();
    private HumanoidModel<?> empty;
    private AresAccessoryArmor(){}
    public static HumanoidModel<?> emptyModel(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        for(String name:new String[]{"head","hat","body","left_arm","right_arm","left_leg","right_leg"})root.addOrReplaceChild(name,CubeListBuilder.create(),PartPose.ZERO);
        return new HumanoidModel<>(LayerDefinition.create(mesh,16,16).bakeRoot());
    }
    @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity,ItemStack item,EquipmentSlot slot,HumanoidModel<?> original){
        if(!AresAccessoryVisuals.ysmPresent())return original;
        if(empty==null)empty=emptyModel();return empty;
    }
}
