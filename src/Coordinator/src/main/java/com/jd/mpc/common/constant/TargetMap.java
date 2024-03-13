package com.jd.mpc.common.constant;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**s
 * 
 * @date 2021/11/24 6:32 下午
 * 该类已由TargetMapHolder代替update by yezhenyue on 20220411
 * @see com.jd.mpc.storage.TargetMapHolder
 *
 */
public class TargetMap extends ConcurrentHashMap<String, Set<String>> {

    private static volatile TargetMap targetMap = null;


    private TargetMap() {
    }

    public static TargetMap getInstance() {
        //第一次校验GlobalLock是否为空
        if (targetMap == null) {
            synchronized (TargetMap.class) {
                //第二次校验GlobalLock是否为空
                if (targetMap == null) {
                    targetMap = new TargetMap();
                }
            }
        }
        return targetMap;
    }
}
