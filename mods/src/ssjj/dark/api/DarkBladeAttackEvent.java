package ssjj.dark.api;

import java.util.Objects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.Event;

/**
 * Posted once on the Forge event bus, on the logical server thread, when a
 * validated dark crystal swing reaches its delayed strike. A miss also posts.
 * This is a notification, not a cancellable damage or target-selection event.
 */
public final class DarkBladeAttackEvent extends Event {
    public enum Action { LIGHT, HEAVY }

    private final ServerPlayer owner;
    private final ItemStack weapon;
    private final Action action;
    private final int combo, grade;
    private final Vec3 origin, direction;
    private final long gameTick;

    public DarkBladeAttackEvent(ServerPlayer owner, ItemStack weapon, Action action,
                               int combo, int grade, Vec3 origin, Vec3 direction, long gameTick) {
        this.owner = Objects.requireNonNull(owner);
        this.weapon = Objects.requireNonNull(weapon).m_41777_();
        this.action = Objects.requireNonNull(action);
        if (grade < 0 || grade > 2 || action == Action.LIGHT && (combo < 0 || combo > 1)
            || action == Action.HEAVY && combo != -1) throw new IllegalArgumentException("Invalid blade phase");
        this.combo = combo;
        this.grade = grade;
        this.origin = Objects.requireNonNull(origin);
        this.direction = Objects.requireNonNull(direction).m_82541_();
        this.gameTick = gameTick;
    }

    public ServerPlayer getOwner() { return owner; }
    /** Each call returns an independent copy; changing it cannot change the held gun. */
    public ItemStack getWeapon() { return weapon.m_41777_(); }
    public Action getAction() { return action; }
    /** Light A=0, light B=1; heavy has no light combo and returns -1. */
    public int getCombo() { return combo; }
    /** Base=0, first upgrade=1, second upgrade=2. */
    public int getGrade() { return grade; }
    public Vec3 getOrigin() { return origin; }
    public Vec3 getDirection() { return direction; }
    /** Authoritative overworld game time shared across dimensions. */
    public long getGameTick() { return gameTick; }

    public ServerPlayer owner() { return getOwner(); }
    public ItemStack weapon() { return getWeapon(); }
    public Action action() { return getAction(); }
    public int combo() { return getCombo(); }
    public int grade() { return getGrade(); }
    public Vec3 origin() { return getOrigin(); }
    public Vec3 direction() { return getDirection(); }
    public long gameTick() { return getGameTick(); }
}
