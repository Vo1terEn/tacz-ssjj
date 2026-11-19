package cn.ssjjares;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.logging.LogUtils;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;

/** Shared decoration lookup. Curios contents never participate in setTier. */
public final class AresAccessories {
    public static final List<String> PARTS=List.of("conquest_will","extinction_resolve","ruin_wings");
    private static final String[] CURIO_SLOTS={"head","body","back"};
    private static final EquipmentSlot[] ARMOR_SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS};
    private static Binding binding;
    private static volatile boolean attempted;
    private static boolean warned;
    private AresAccessories(){}
    public static int kind(ItemStack stack){return stack.getItem() instanceof AresAccessoryItem item?item.kind():0;}
    public static int tier(ItemStack stack){return stack.getItem() instanceof AresAccessoryItem item?item.tier():0;}
    public static boolean hasCurios(){var mods=ModList.get();return mods!=null&&mods.isLoaded("curios");}
    /** The native armor instance wins so one accessory kind is drawn only once. */
    public static ItemStack equipped(LivingEntity entity,int kind){
        if(entity==null||kind<1||kind>3)return ItemStack.EMPTY;
        var armor=entity.getItemBySlot(ARMOR_SLOTS[kind-1]);
        return kind(armor)==kind?armor:curio(entity,kind);
    }
    /** Only a visible functional Curios slot; cosmetic slots do not affect armor. */
    public static ItemStack curio(LivingEntity entity,int kind){
        return findCurio(entity, kind, true);
    }
    static ItemStack functionalCurio(LivingEntity entity, int kind) {
        return findCurio(entity, kind, false);
    }
    private static ItemStack findCurio(LivingEntity entity, int kind, boolean visible) {
        return findCurio(entity, kind, visible, stack -> true);
    }
    static ItemStack functionalCurio(LivingEntity entity, int kind, Predicate<ItemStack> usable) {
        return findCurio(entity, kind, false, usable);
    }
    private static ItemStack findCurio(LivingEntity entity, int kind, boolean visible, Predicate<ItemStack> usable) {
        if(entity==null||kind<1||kind>3)return ItemStack.EMPTY;
        var b=binding();if(b==null)return ItemStack.EMPTY;
        try{
            var capability=(LazyOptional<?>)b.inventory.invoke(null,entity);var inv=capability.resolve().orElse(null);if(inv==null)return ItemStack.EMPTY;
            var handler=((Optional<?>)b.stacksHandler.invoke(inv,CURIO_SLOTS[kind-1])).orElse(null);if(handler==null)return ItemStack.EMPTY;
            var stacks=(IItemHandler)b.stacks.invoke(handler);
            var renders=visible ? (List<?>)b.renders.invoke(handler) : List.of();
            for(int i=0;i<stacks.getSlots();i++){
                var stack=stacks.getStackInSlot(i);
                if(kind(stack)==kind&&(i>=renders.size()||!Boolean.FALSE.equals(renders.get(i)))&&usable.test(stack))return stack;
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){warn(failure);}
        return ItemStack.EMPTY;
    }
    /** Mechanical effects use the functional stacks, independent of the render toggles. */
    static void forEachFunctionalCurio(LivingEntity entity, Consumer<ItemStack> visitor){
        var b=binding();
        if(b==null)return;
        try{
            var capability=(LazyOptional<?>)b.inventory.invoke(null,entity);
            var inv=capability.resolve().orElse(null);
            if(inv==null)return;
            for(int i=0;i<CURIO_SLOTS.length;i++){
                var handler=((Optional<?>)b.stacksHandler.invoke(inv,CURIO_SLOTS[i])).orElse(null);
                if(handler==null)continue;
                var stacks=(IItemHandler)b.stacks.invoke(handler);
                for(int index=0;index<stacks.getSlots();index++){
                    var stack=stacks.getStackInSlot(index);
                    if(kind(stack)==i+1)visitor.accept(stack);
                }
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError failure){warn(failure);}
    }
    public static void registerCurios(){
        var b=binding();if(b==null)return;
        try{for(var part:PARTS)for(var suffix:List.of("","_absolute"))b.register.invoke(null,BuiltInRegistries.ITEM.get(AresArmorItems.id(part+suffix)),b.behavior);}
        catch(ReflectiveOperationException|RuntimeException|LinkageError failure){warn(failure);}
    }
    private static Binding binding(){
        if(!attempted) initialize();
        return binding;
    }
    private static synchronized void initialize(){
        if(attempted||ModList.get()==null)return;
        try{if(hasCurios())binding=new Binding();}catch(ReflectiveOperationException|RuntimeException|LinkageError failure){warn(failure);}
        attempted=true;
    }
    private static synchronized void warn(Throwable failure){if(!warned){warned=true;LogUtils.getLogger().warn("Ares Curios decoration adapter unavailable; native armor slots remain usable",failure);}}
    /** Curios types occur only as reflection strings, never JVM descriptors. */
    private static final class Binding {
        final Method inventory,stacksHandler,stacks,renders,register,identifier,cosmetic;final Object behavior;
        Binding() throws ReflectiveOperationException {
            String p="top.theillusivec4.curios.api.";var loader=AresAccessories.class.getClassLoader();
            var api=Class.forName(p+"CuriosApi",false,loader);var curioItem=Class.forName(p+"type.capability.ICurioItem",false,loader);
            var inventoryType=Class.forName(p+"type.capability.ICuriosItemHandler",false,loader);var stacksType=Class.forName(p+"type.inventory.ICurioStacksHandler",false,loader);var context=Class.forName(p+"SlotContext",false,loader);
            inventory=api.getMethod("getCuriosInventory",LivingEntity.class);stacksHandler=inventoryType.getMethod("getStacksHandler",String.class);stacks=stacksType.getMethod("getStacks");renders=stacksType.getMethod("getRenders");register=api.getMethod("registerCurio",Item.class,curioItem);identifier=context.getMethod("identifier");cosmetic=context.getMethod("cosmetic");
            behavior=Proxy.newProxyInstance(loader,new Class<?>[]{curioItem},this::invoke);
        }
        Object invoke(Object proxy,Method method,Object[] args) throws Throwable {
            switch(method.getName()){
                case "getAttributeModifiers":{
                    if(args.length!=3||((Boolean)cosmetic.invoke(args[0])))return ImmutableMultimap.of();
                    var stack=(ItemStack)args[2];
                    int kind=kind(stack);
                    if(kind==0||!CURIO_SLOTS[kind-1].equals(identifier.invoke(args[0])))return ImmutableMultimap.of();
                    return AresCombat.accessoryModifiers(stack,(UUID)args[1]);
                }
                case "canEquip":{
                    String slot=args[0] instanceof String name?name:(String)identifier.invoke(args[0]);
                    var stack=(ItemStack)args[args.length-1];int kind=kind(stack);
                    return kind>0&&CURIO_SLOTS[kind-1].equals(slot)&&(args[0] instanceof String||!((Boolean)cosmetic.invoke(args[0])));
                }
                case "canEquipFromUse":return true;
                case "toString":return "Ares accessory";
                case "hashCode":return System.identityHashCode(proxy);
                case "equals":return proxy==args[0];
                default:if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);throw new UnsupportedOperationException(method.toString());
            }
        }
    }
}
