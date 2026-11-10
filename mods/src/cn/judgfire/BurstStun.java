package cn.judgfire;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The old registry entry only lets existing saves load and migrate safely. */
public final class BurstStun extends MobEffect {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "judgfire_runtime");
    public static final RegistryObject<MobEffect> STUN = EFFECTS.register("red_lotus_stun", BurstStun::new);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "judgfire_runtime");
    public static final RegistryObject<SoundEvent> SOUND = SOUNDS.register("red_lotus_burst", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("judgfire_runtime", "red_lotus_burst")));
    public static final String IMMUNE = "JudgfireStunImmuneUntil";

    private BurstStun() { super(MobEffectCategory.HARMFUL, 0xFF6324); }
    public static boolean stunned(LivingEntity entity) { return StunState.stunned(entity); }
    /** Red Lotus refuses a second application during its active/immunity windows. */
    public static boolean apply(LivingEntity entity, int duration) { return StunState.apply(entity, duration, false); }
    /** Dimension fields retain their existing refresh behavior, including immunity bypass. */
    public static boolean refresh(LivingEntity entity, int duration) { return StunState.apply(entity, duration, true); }
}
