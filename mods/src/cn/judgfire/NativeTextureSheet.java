package cn.judgfire;

/** Native lifetime texture-sheet curves. The frame range may cover only part of an atlas. */
final class NativeTextureSheet {
  private NativeTextureSheet(){}
  static int frame(int[] sheet,float[][] overTime,float[][] startFrame,float cycles,double phase,float random){
    int cols=sheet==null?1:Math.max(1,sheet[0]),rows=sheet==null?1:Math.max(1,sheet[1]);
    int count=cols*rows;if(count<=1)return 0;
    // A fixed random factor is supplied from particle birth, never from the current frame.
    // Cycle the curve's lifetime domain so constant/random source frames remain constant.
    double time=Math.max(0,Math.min(Math.nextDown(1d),phase));
    if(cycles>0&&cycles!=1)time=(time*cycles)%1;
    double value=sample(overTime,time,random,time)+sample(startFrame,0,random,0);
    value-=Math.floor(value);
    return Math.min(count-1,Math.max(0,(int)Math.floor(value*count)));
  }
  private static double sample(float[][] values,double time,float random,double fallback){
    if(values==null||values.length==0)return fallback;
    double at=Math.max(0,Math.min(1,time))*(values.length-1);int a=(int)at,b=Math.min(a+1,values.length-1);
    double mix=at-a,lo=values[a][0]+(values[b][0]-values[a][0])*mix;
    double hi=values[a][1]+(values[b][1]-values[a][1])*mix;
    return lo+(hi-lo)*Math.max(0,Math.min(1,random));
  }
}
