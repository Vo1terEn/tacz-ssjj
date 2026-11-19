package cn.ssjjhyper.client;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** One original TORSO_SKILL raise2 sound per acknowledged return batch. */
public final class HyperCoreAudio {
    private static final LinkedHashSet<UUID> RECENT=new LinkedHashSet<>();
    private static final Set<String> PROFILES=Set.of("hyper_revolver","hyper_revolver_totem","hyper_revolver_star","hyper_revolver_colorful_spar","hyper_revolver1","hyper_revolver1_totem","hyper_revolver1_star","hyper_revolver1_colorful_spar","hyper_revolver2","hyper_revolver2_totem","hyper_revolver2_star","hyper_revolver2_colorful_spar");
    private static WeakReference<ClientLevel> world=new WeakReference<>(null);
    private HyperCoreAudio(){}
    public record Cue(ResourceLocation event,Vec3 position){}
    static Cue cue(String profile,Vec3 position,long tick,long now){
        if(!PROFILES.contains(profile==null?"":profile)||position==null||!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)||tick<0||now-tick< -2||now-tick>20)return null;
        String gun=profile.startsWith("hyper_revolver2")?"hyper_revolver2":profile.startsWith("hyper_revolver1")?"hyper_revolver1":profile.equals("hyper_revolver")||profile.startsWith("hyper_revolver_")?"hyper_revolver":null;
        return gun==null?null:new Cue(new ResourceLocation("ssjj_hyper","core.return."+gun),position);
    }
    static boolean remember(UUID id){if(id==null||!RECENT.add(id))return false;while(RECENT.size()>512)RECENT.remove(RECENT.iterator().next());return true;}
    static SimpleSoundInstance sound(Cue cue){var p=cue.position();return new SimpleSoundInstance(cue.event(),SoundSource.PLAYERS,1,1,RandomSource.create(),false,0,SoundInstance.Attenuation.LINEAR,p.x,p.y,p.z,false);}
    public static void returning(String profile,UUID id,Vec3 position,long tick){
        var mc=Minecraft.getInstance();if(mc==null)return;var next=mc.level;if(world.get()!=next){RECENT.clear();world=new WeakReference<>(next);}if(next==null||mc.player==null)return;
        var cue=cue(profile,position,tick,next.getGameTime());if(cue!=null&&remember(id))mc.getSoundManager().play(sound(cue));
    }
}
