package cn.punishmenteye;

/** Camera safety is evaluated at render time, including immediate perspective switches. */
public final class PhantomVisibility {
    public static boolean visible(boolean firstPerson,boolean cameraOwner,double dx,double dy,double dz,double height){
        if(firstPerson&&cameraOwner)return false;
        return !(dx*dx+dz*dz<.8*.8&&dy>-.3&&dy<height+.3);
    }
}
