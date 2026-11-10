package cn.judgfire;
/** Refund receipt only. Actual damage and gun Post amounts keep the measured health loss. */
public final class CrownAmmoReceipt {
 private CrownAmmoReceipt() {}
 public static double confirmed(double loss, boolean accepted, boolean charged) {
  if (Double.isFinite(loss) && loss > 0) return loss;
  return accepted && charged ? 1.0 : 0.0;
 }
}
