package cn.ssjjhyper.client;

import cn.ssjjdimension.client.DimensionParticleEffects;
import cn.ssjjdimension.client.DimensionParticles;
import com.google.gson.Gson;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Original cosmetic body layers, sampled and queued by the installed shared FX path. */
public final class HyperBodyEffects {
    public static final class Profile { public int schema; public String profile; public DimensionParticles.Data[] mounts; }
    private static volatile Map<String,Map<String,DimensionParticles.Data>> profiles=Map.of();
    private HyperBodyEffects() {}
    public static void read(ResourceManager resources) {
        profiles=Map.of();
        var fresh=new LinkedHashMap<String,Map<String,DimensionParticles.Data>>();
        for(String gun:List.of("hyper_revolver","hyper_revolver1","hyper_revolver2"))
            for(String skin:List.of("","totem","star","colorful_spar")) {
                String stem=gun+(skin.isEmpty()?"":"_"+skin);
                try(var stream=resources.open(new ResourceLocation("ssjj_hyper","effects/body_"+stem+".json"));
                    var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
                    var source=new Gson().fromJson(reader,Profile.class);
                    int count=skin.isEmpty()&&!gun.equals("hyper_revolver2")?2:4;
                    if(source==null||source.schema!=1||!stem.equals(source.profile)||source.mounts==null||source.mounts.length!=count)
                        throw new IllegalArgumentException("Invalid Hyper source body profile "+stem);
                    var mounts=new LinkedHashMap<String,DimensionParticles.Data>();
                    for(int i=0;i<count;i++) {
                        String bone="weapon_particle_effect"+(i==0?"":Integer.toString(i+1));
                        var data=source.mounts[i];
                        DimensionParticles.validate(data,"ssjj_hyper:"+gun,bone,"ssjj_hyper:textures/effects/");
                        for(var emitter:data.emitters)
                            if(resources.getResource(new ResourceLocation(emitter.texture)).isEmpty())
                                throw new IllegalArgumentException("Missing Hyper body texture "+emitter.texture);
                        mounts.put(bone,data);
                    }
                    fresh.put(stem,Map.copyOf(mounts));
                } catch(Exception failure) { LogUtils.getLogger().error("Cannot load Hyper source body effects "+stem,failure); }
            }
        profiles=Map.copyOf(fresh);
        LogUtils.getLogger().info("HYPER_FAMILY source body profiles loaded: {}",fresh.size());
    }
    /** Includes source animation-only mounts that have no gun triangles. */
    public static List<String> bones(String resourceStem) {
        var selected=profiles.get(resourceStem);
        if(selected==null)return List.of();
        return selected.keySet().stream().sorted().toList();
    }
    /** The current animated local pose keeps these layers attached in every view. */
    public static void defer(BedrockAnimatedModel model,String resourceStem,String bone,PoseStack pose) {
        var selected=profiles.get(resourceStem);
        if(selected!=null)DimensionParticleEffects.defer(model,bone,pose,selected.get(bone));
    }
}
