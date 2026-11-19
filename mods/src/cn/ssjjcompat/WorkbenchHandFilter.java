package cn.ssjjcompat;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.gui.components.GunPackList;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Keeps TaCZ's native hand filter and selection in sync; never changes install permissions. */
@OnlyIn(Dist.CLIENT)
public final class WorkbenchHandFilter {
    private static final Set<String> NAMESPACES=Set.of("eyeofpunishment","judgfire","ssjj_dimension","ssjj_hyper","ssjj_zeus","ssjjparasite2");
    private static final Map<GunSmithTableScreen,Boolean> LAST_BY_HAND=new WeakHashMap<>();
    private static final Map<GunSmithTableScreen,Set<String>> LAST_NAMESPACES=new WeakHashMap<>();
    private static final Map<GunSmithTableScreen,String> LAST_SEARCH=new WeakHashMap<>();
    private static final Field FILTER=field(GunSmithTableScreen.class,"filterList"),
        CHECKBOX=field(GunPackList.class,"byHandCheckbox"),
        SELECTED=field(GunPackList.Checkbox.class,"selected"),
        KEYS=field(GunSmithTableScreen.class,"recipeKeys"),
        RECIPES=field(GunSmithTableScreen.class,"recipes"),
        TYPE=field(GunSmithTableScreen.class,"selectedType"),
        LIST=field(GunSmithTableScreen.class,"selectedRecipeList"),
        RECIPE=field(GunSmithTableScreen.class,"selectedRecipe"),
        TYPE_PAGE=field(GunSmithTableScreen.class,"typePage"),
        INDEX_PAGE=field(GunSmithTableScreen.class,"indexPage");
    private static final Method RESOLVE=method("getSelectedRecipe",ResourceLocation.class),
        INGREDIENTS=method("getPlayerIngredientCount",GunSmithTableRecipe.class);
    private WorkbenchHandFilter() {}
    private static Field field(Class<?> owner,String name) {
        try {var f=owner.getDeclaredField(name);f.setAccessible(true);return f;}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static Method method(String name,Class<?> argument) {
        try {var m=GunSmithTableScreen.class.getDeclaredMethod(name,argument);m.setAccessible(true);return m;}
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    /** Called once per native classification, after the list has been constructed. */
    public static void beforeClassify(GunSmithTableScreen screen) {
        try {
            var filter=(GunPackList)FILTER.get(screen);
            if(filter==null)return;
            var checkbox=CHECKBOX.get(filter);boolean checked=SELECTED.getBoolean(checkbox);
            var previous=LAST_BY_HAND.putIfAbsent(screen,checked);
            var previousNames=LAST_NAMESPACES.put(screen,Set.copyOf(filter.namespaceList()));
            var previousSearch=LAST_SEARCH.put(screen,filter.getSearchText());
            if(previous!=null) {
                // TaCZ's filter handlers reset the index after init has rebuilt the buttons.
                // Reset before classification as well so those buttons already show page zero.
                if(previous.booleanValue()!=checked||!LAST_NAMESPACES.get(screen).equals(previousNames)||!LAST_SEARCH.get(screen).equals(previousSearch)) {
                    LAST_BY_HAND.put(screen,checked);
                    TYPE_PAGE.setInt(screen,0);INDEX_PAGE.setInt(screen,0);
                }
                return;
            }
            var client=Minecraft.getInstance();
            if(client.player==null)return;
            var held=client.player.getMainHandItem();var gun=IGun.getIGunOrNull(held);
            if(gun==null||!NAMESPACES.contains(gun.getGunId(held).getNamespace()))return;
            SELECTED.setBoolean(checkbox,true);LAST_BY_HAND.put(screen,Boolean.TRUE);
            TYPE.set(screen,null);LIST.set(screen,null);
            TYPE_PAGE.setInt(screen,0);INDEX_PAGE.setInt(screen,0);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot initialize TaCZ workbench hand filter",failure);}
    }
    /** Native tabs may disappear after filtering; move all dependent state together. */
    @SuppressWarnings("unchecked")
    public static void afterClassify(GunSmithTableScreen screen) {
        try {
            // The constructor's first pass has no filter widget yet and keeps native behavior.
            if(FILTER.get(screen)==null)return;
            var keys=(Map<ResourceLocation,?>)KEYS.get(screen);
            var recipes=(Map<ResourceLocation,List<ResourceLocation>>)RECIPES.get(screen);
            var selected=(ResourceLocation)TYPE.get(screen);
            if(!keys.containsKey(selected)) {
                selected=keys.isEmpty()?null:keys.keySet().iterator().next();
                TYPE.set(screen,selected);TYPE_PAGE.setInt(screen,0);INDEX_PAGE.setInt(screen,0);
            }
            var list=selected==null?null:recipes.get(selected);
            LIST.set(screen,list);
            int typePage=Math.max(0,Math.min(TYPE_PAGE.getInt(screen),Math.max(0,(keys.size()-1)/7)));
            TYPE_PAGE.setInt(screen,typePage);
            if(list==null||list.isEmpty()||INDEX_PAGE.getInt(screen)<0||INDEX_PAGE.getInt(screen)*6>=list.size())INDEX_PAGE.setInt(screen,0);
            var old=(GunSmithTableRecipe)RECIPE.get(screen);
            int oldIndex=old==null||list==null?-1:list.indexOf(old.getId());
            int pageStart=INDEX_PAGE.getInt(screen)*6;
            if(oldIndex>=pageStart&&oldIndex<pageStart+6)return;
            var first=list==null||list.isEmpty()?null:list.get(INDEX_PAGE.getInt(screen)*6);
            var current=first==null?null:(GunSmithTableRecipe)RESOLVE.invoke(screen,first);
            RECIPE.set(screen,current);INGREDIENTS.invoke(screen,current);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot reconcile TaCZ workbench selection",failure);}
    }
}
