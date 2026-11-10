package cn.punishmenteye;
/** Generated from confirmed project config; one accepted enemy hit per refund. */
public final class AmmoRefundRules {
 public static final int CAPACITY=45,PER_ENEMY=1;
 public static int afterHit(int current,boolean accepted){return !accepted||current>=CAPACITY?current:Math.min(CAPACITY,Math.max(0,current)+PER_ENEMY);}
}
