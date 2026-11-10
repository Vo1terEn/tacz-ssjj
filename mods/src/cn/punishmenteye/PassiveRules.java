package cn.punishmenteye;
/** Generated from config/project.json by build.py; edit the config for defaults. */
public final class PassiveRules {
    public static final int DEFAULT_MARK_SECONDS=10, PHANTOM_TICKS=10, MAX_PHANTOMS=160, PHANTOM_INTERVAL=4;
    public static final float MULTIPLIER=1.5f, OPACITY=0.38f;
    public static float damage(float base,boolean marked){return marked?base*MULTIPLIER:base;}
    public static boolean moving(double dx,double dz){double d=dx*dx+dz*dz;return d>0.0004&&d<16;}
    public static float alpha(float age){return Math.max(0,OPACITY*(1-age/PHANTOM_TICKS));}
}
