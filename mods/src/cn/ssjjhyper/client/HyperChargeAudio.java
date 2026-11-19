package cn.ssjjhyper.client;

import cn.ssjjhyper.HyperChargeNetwork;
import java.lang.ref.WeakReference;
import java.util.LinkedHashSet;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** One positional source sound per accepted server event, including remote shooters and dead targets. */
public final class HyperChargeAudio {
    private static final int MAX_AGE=20, MAX_RECENT=512;
    private static final LinkedHashSet<UUID> recent=new LinkedHashSet<>();
    private static WeakReference<ClientLevel> level=new WeakReference<>(null);
    private HyperChargeAudio() {}
    public record Cue(ResourceLocation event,Vec3 position) {}

    static Cue cue(HyperChargeNetwork.Visual v,ResourceLocation dimension,long now) {
        if(v==null||!v.valid()||dimension==null||!dimension.equals(v.dimension())||(v.type()!=2&&v.type()!=6))return null;
        if(v.type()==6&&!v.owner().equals(v.targetUuid()))return null;
        long age=now-v.tick();if(age< -2||age>MAX_AGE)return null;
        String tier=v.profile().startsWith("hyper_revolver2")?"hyper_revolver2":"hyper_revolver1";
        return new Cue(new ResourceLocation("ssjj_hyper","charge."+tier+(v.type()==6?".cast":".detonate")),v.center());
    }
    static boolean remember(UUID id) {
        if(id==null||!recent.add(id))return false;
        while(recent.size()>MAX_RECENT)recent.remove(recent.iterator().next());
        return true;
    }
    static SimpleSoundInstance sound(Cue cue) {
        var p=cue.position();
        // Standard sounds.json resources; mono originals permit native spatial attenuation.
        // Position remains valid after a killed target disappears from the entity tracker.
        return new SimpleSoundInstance(cue.event(),SoundSource.PLAYERS,1,1,RandomSource.create(),false,0,
            SoundInstance.Attenuation.LINEAR,p.x,p.y,p.z,false);
    }
    public static void accept(HyperChargeNetwork.Visual visual) {
        var mc=Minecraft.getInstance();if(mc==null)return;
        var current=mc.level;
        if(level.get()!=current){recent.clear();level=new WeakReference<>(current);}
        if(current==null||mc.player==null)return;
        var next=cue(visual,current.dimension().location(),current.getGameTime());
        if(next!=null&&remember(visual.id()))mc.getSoundManager().play(sound(next));
    }
}
