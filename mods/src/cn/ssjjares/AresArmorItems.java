package cn.ssjjares;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.RegisterEvent;

/** Native armor slots and modifiers; only a complete matching set selects the character appearance. */
@Mod.EventBusSubscriber(modid="eye_judgment_arsenal",bus=Mod.EventBusSubscriber.Bus.MOD)
public final class AresArmorItems {
    public static final String NAMESPACE="ssjj_ares";
    public static final List<String> PARTS=List.of("extinction_mask","extinction_chestplate","extinction_belt","extinction_boots");
    public static final List<EquipmentSlot> SLOTS=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET);
    public static final ArmorMaterial MATERIAL=new ArmorMaterial(){
        public int getDurabilityForType(ArmorItem.Type type){return 0;}
        public int getDefenseForType(ArmorItem.Type type){return switch(type){case HELMET,BOOTS->7;case CHESTPLATE,LEGGINGS->8;};}
        public int getEnchantmentValue(){return 15;}
        public SoundEvent getEquipSound(){return SoundEvents.ARMOR_EQUIP_NETHERITE;}
        public Ingredient getRepairIngredient(){return Ingredient.EMPTY;}
        public String getName(){return NAMESPACE+":extinction";}
        public float getToughness(){return 5;}
        public float getKnockbackResistance(){return 0;}
    };
    private AresArmorItems(){}
    public static ResourceLocation id(String path){return new ResourceLocation(NAMESPACE,path);}
    public static boolean isArmorId(ResourceLocation id){
        if(id==null||!NAMESPACE.equals(id.getNamespace()))return false;
        String path=id.getPath();return PARTS.contains(path)||AresAccessories.PARTS.contains(path)||path.endsWith("_absolute")&&(PARTS.contains(path.substring(0,path.length()-9))||AresAccessories.PARTS.contains(path.substring(0,path.length()-9)));
    }
    /** 0: incomplete/mixed, 1: original Ares, 2: Ares skin. No equipment or player mutation. */
    public static int setTier(LivingEntity entity){
        int tier=0;
        for(int i=0;i<PARTS.size();i++){
            var stack=entity.getItemBySlot(SLOTS.get(i));if(stack.isEmpty())return 0;
            var key=BuiltInRegistries.ITEM.getKey(stack.getItem());
            int partTier=partTier(key,i);
            if(partTier==0||tier!=0&&tier!=partTier)return 0;tier=partTier;
        }
        return tier;
    }
    private static int partTier(ResourceLocation key,int index){
        if(id(PARTS.get(index)).equals(key))return 1;
        if(id(PARTS.get(index)+"_absolute").equals(key))return 2;
        if(index<3){if(id(AresAccessories.PARTS.get(index)).equals(key))return 1;if(id(AresAccessories.PARTS.get(index)+"_absolute").equals(key))return 2;}
        return 0;
    }
    @SubscribeEvent public static void register(RegisterEvent event){
        event.register(Registries.ITEM,helper->{
            helper.register(id("golden_charm_stone"),new Item(new Item.Properties()));
            ArmorItem.Type[] types={ArmorItem.Type.HELMET,ArmorItem.Type.CHESTPLATE,ArmorItem.Type.LEGGINGS,ArmorItem.Type.BOOTS};
            for(int i=0;i<PARTS.size();i++)for(boolean absolute:new boolean[]{false,true})
                helper.register(id(PARTS.get(i)+(absolute?"_absolute":"")),new AresArmorItem(types[i]));
            for(int i=0;i<AresAccessories.PARTS.size();i++)for(boolean absolute:new boolean[]{false,true})
                helper.register(id(AresAccessories.PARTS.get(i)+(absolute?"_absolute":"")),new AresAccessoryItem(types[i],i+1,absolute));
        });
    }
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent event){
        if(event.getTabKey().equals(CreativeModeTabs.INGREDIENTS))event.accept(BuiltInRegistries.ITEM.get(id("golden_charm_stone")));
        if(event.getTabKey().equals(CreativeModeTabs.COMBAT))for(var part:PARTS)for(var suffix:List.of("","_absolute"))event.accept(BuiltInRegistries.ITEM.get(id(part+suffix)));
        if(event.getTabKey().equals(CreativeModeTabs.COMBAT))for(var part:AresAccessories.PARTS)for(var suffix:List.of("","_absolute"))event.accept(BuiltInRegistries.ITEM.get(id(part+suffix)));
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event){event.enqueueWork(AresAccessories::registerCurios);}
}
