package cn.ssjjdimension.client;

import cn.ssjjdimension.DimensionFieldRules;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.client.gameplay.LocalPlayerDataHolder;

/** Native client lock covers the interval before server reload state arrives. */
public final class DimensionFieldInputRules {
    private DimensionFieldInputRules() {}
    public static boolean actionReady(IGunOperator operator,LocalPlayerDataHolder data){
        return data!=null&&!data.clientStateLock&&!data.isBolting&&DimensionFieldRules.actionReady(operator);
    }
}
