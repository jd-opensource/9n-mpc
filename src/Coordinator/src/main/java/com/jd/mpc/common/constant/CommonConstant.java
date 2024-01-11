package com.jd.mpc.common.constant;

/**
 * @Description: 常量
 * 
 * @Date: 2022/3/22
 */
public interface CommonConstant {

    /** coordinator服务名称 */
    String COORDINATOR_NAME = "coordinator";

    String BIN_BASH = "/bin/bash";

    String NN_MPC_POD_NAME_STR = "-mpc-nn-worker-";

    String K8S_DATA_VOLUME = "data";

    String K8S_LOG_VOLUME = "logs";

    /**
     * k8s's yaml in nacos
     */
    String K8S_GROUP = "K8S_GROUP";

    /**
     * functor's default properties in nacos
     */
    String FUNCTOR_GROUP = "FUNCTOR_GROUP";
    /**
     * DEFAULT_GROUP
     */
    String DEFAULT_GROUP = "DEFAULT_GROUP";

}
