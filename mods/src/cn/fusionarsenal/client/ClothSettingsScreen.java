package cn.fusionarsenal.client;

import cn.fusionarsenal.config.ArsenalConfig;
import cn.fusionarsenal.config.ConfigEditing;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import me.shedaniel.clothconfig2.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** New builders and drafts for every opening; nothing is written by cancel or by editing a field. */
public final class ClothSettingsScreen {
    private static final String SCHEMA="/assets/eye_judgment_arsenal/config/screen.json";
    private ClothSettingsScreen() {}

    public static Screen create(Screen parent) {
        try { return build(parent); }
        catch (RuntimeException failure) {
            return new AlertScreen(() -> Minecraft.getInstance().setScreen(parent),
                Component.literal("无法打开枪包配置"), Component.literal(message(failure)),
                Component.literal("返回"), false);
        }
    }

    private static Screen build(Screen parent) {
        var initial=ConfigEditing.snapshot();
        var draft=new LinkedHashMap<>(initial);
        var defaults=ArsenalConfig.defaults();
        var root=schema();
        var builder=ConfigBuilder.create().setParentScreen(parent)
            .setTitle(Component.literal("[SSJJRPG] 生死狙击冒险枪包配置"))
            .setDoesConfirmSave(true).setAlwaysShowTabs(true);
        var entries=builder.entryBuilder();
        var seen=new LinkedHashSet<String>();
        for (var sectionValue:root.getAsJsonArray("sections")) {
            var section=sectionValue.getAsJsonObject();
            var category=builder.getOrCreateCategory(Component.literal(section.get("title").getAsString()));
            boolean remoteReadOnly=!ConfigEditing.editable("fire.damage_base");
            category.addEntry(entries.startTextDescription(Component.literal(remoteReadOnly
                ? "服务器玩法参数为只读；本机显示设置可修改。保存位置：config/ssjjrpg.toml。"
                : "保存到 config/ssjjrpg.toml。技能时间按下次激活生效；配件属性请重新装备后核对。" )).build());
            for (var groupValue:section.getAsJsonArray("groups")) {
                var group=groupValue.getAsJsonObject();
                var sub=entries.startSubCategory(Component.literal(group.get("title").getAsString())).setExpanded(true);
                for (var optionValue:group.getAsJsonArray("entries")) {
                    var option=optionValue.getAsJsonObject();
                    String key=option.get("key").getAsString();
                    if (!seen.add(key) || !initial.containsKey(key) || !defaults.containsKey(key))
                        throw new IllegalStateException("配置条目与模组版本不一致："+key);
                    var entry=field(entries,option,initial.get(key),defaults.get(key),draft);
                    entry.setEditable(ConfigEditing.editable(key));
                    entry.appendSearchTags(List.of(key));
                    sub.add(entry);
                }
                category.addEntry(sub.build());
            }
        }
        // Layout follows weapon/attachment ownership rather than storage declaration order.
        // Keep the retired eye_damage key readable for old configs without showing a dead control.
        var visibleDefaults = new LinkedHashSet<>(defaults.keySet());
        var visibleInitial = new LinkedHashSet<>(initial.keySet());
        visibleDefaults.remove("attachment_bonus.eye_damage");
        visibleInitial.remove("attachment_bonus.eye_damage");
        if (!seen.equals(visibleDefaults) || !seen.equals(visibleInitial))
            throw new IllegalStateException("配置清单与当前版本不一致。");
        Screen[] current=new Screen[1];
        builder.setSavingRunnable(() -> {
            var changes=new LinkedHashMap<String,Object>();
            draft.forEach((key,value) -> {if (!Objects.equals(initial.get(key),value)) changes.put(key,value);});
            if (changes.isEmpty()) return;
            try { ConfigEditing.save(Collections.unmodifiableMap(changes)); }
            catch (RuntimeException failure) {
                var minecraft=Minecraft.getInstance();
                // Cloth returns to the parent after its saving callback. Queue the error for the next UI tick.
                minecraft.tell(() -> minecraft.setScreen(new AlertScreen(() -> minecraft.setScreen(current[0]),
                    Component.literal("配置保存失败"), Component.literal(message(failure)),
                    Component.literal("返回配置"), false)));
            }
        });
        current[0]=builder.build();
        return current[0];
    }

    private static AbstractConfigListEntry<?> field(ConfigEntryBuilder builder, JsonObject option,
            Object value, Object defaultValue, Map<String,Object> draft) {
        String key=option.get("key").getAsString();
        var label=Component.literal(option.get("label").getAsString());
        var tooltip=new ArrayList<Component>();
        for (var line:option.getAsJsonArray("tooltip")) tooltip.add(Component.literal(line.getAsString()));
        if (!option.get("min").isJsonNull()) tooltip.add(Component.literal("允许范围："+option.get("min").getAsString()+" ～ "+option.get("max").getAsString()));
        boolean editable=ConfigEditing.editable(key);
        if (!editable) tooltip.add(Component.literal("由当前服务器管理，无法在本机修改。"));
        Component[] tips=tooltip.toArray(Component[]::new);
        if (value instanceof Boolean flag)
            return builder.startBooleanToggle(label,flag).setDefaultValue((Boolean)defaultValue)
                .setYesNoTextSupplier(enabled -> Component.literal(enabled?"开启":"关闭"))
                .setTooltip(tips).setErrorSupplier(v -> validate(key,v))
                .setSaveConsumer(v -> {if (editable) draft.put(key,v);}).build();
        if (value instanceof Integer number)
            return builder.startIntField(label,number).setDefaultValue(((Number)defaultValue).intValue())
                .setMin(option.get("min").getAsInt()).setMax(option.get("max").getAsInt())
                .setTooltip(tips).setErrorSupplier(v -> validate(key,v))
                .setSaveConsumer(v -> {if (editable) draft.put(key,v);}).build();
        return builder.startDoubleField(label,((Number)value).doubleValue())
            .setDefaultValue(((Number)defaultValue).doubleValue())
            .setMin(option.get("min").getAsDouble()).setMax(option.get("max").getAsDouble())
            .setTooltip(tips).setErrorSupplier(v -> validate(key,v))
            .setSaveConsumer(v -> {if (editable) draft.put(key,v);}).build();
    }

    private static Optional<Component> validate(String key,Object value) {
        try {ArsenalConfig.checked(key,value);return Optional.empty();}
        catch (IllegalArgumentException failure) {return Optional.of(Component.literal("请输入允许范围内的有效数值。"));}
    }
    private static JsonObject schema() {
        try (var stream=ClothSettingsScreen.class.getResourceAsStream(SCHEMA)) {
            if (stream==null) throw new IllegalStateException("缺少配置界面说明文件。");
            return JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException failure) {throw new IllegalStateException("读取配置界面失败。",failure);}
    }
    private static String message(RuntimeException failure) {
        String value=failure.getMessage();return value==null||value.isBlank()?"请检查配置文件是否可写，并查看游戏日志。":value;
    }
}
