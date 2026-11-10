package cn.fusionarsenal.api;

import java.util.Set;

/** Explicit weapon integration contract. IDs use the stable namespace:path resource syntax. */
public interface WeaponModule {
    String id();
    Set<String> gunIds();

    /** Register this module's original runtime once on the active Forge construction context. */
    void initialize();
}
