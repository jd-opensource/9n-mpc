package com.jd.mpc.domain.offline.commons;

import java.util.List;
import java.util.Map;

import com.jd.mpc.common.enums.K8sResourceTypeEnum;

import com.jd.mpc.common.enums.StoreTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 
 * @date 2021/12/8 3:21 下午
 */
@Data
@EqualsAndHashCode
public class OfflineTask {

    /**
     * 父任务id
     */
    private String id;

    /**
     * 子id
     */
    private Integer subId;

    /**
     * bdp生产账号
     */
    private String bdpAccount;

    /**
     * 子任务序号
     */
    private Integer taskIndex;

    /**
     * k8s资源类型
     */
    private String resourcesType = K8sResourceTypeEnum.DEPLOYMENT.getName();

    /**
     * crd名称
     */
    private String crdName;

    /**
     * url
     */
    private String url;

    /**
     * workDir
     */
    private String workDir;

    /**
     * 存储类型
     */
    private StoreTypeEnum storeType;

    /**
     * 启动命令
     */
    private List<String> commands;

    /**
     * pod数量 默认为1
     */
    private Integer podNum = 1;

    /**
     * 已完成pod数量
     */
    private Integer completedNum = 0;

    /**
     * pod名称
     */
    private String name;

    /**
     * 任务状态
     */
    private Integer status;

    /**
     * 任务镜像
     */
    private String image;

    /**
     * nntrainer镜像
     */
    private String nnImage;

    /**
     * k8s部署文件路径
     */
    private String deploymentPath;

    /**
     * k8sservice文件路径
     */
    private String servicePath;

    /**
     * k8s serviceName
     */
    private String serviceName;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 任务角色
     */
    private String role;

    /**
     * 任务端
     */
    private String target;

    /**
     * redis地址
     */
    private String redis_server;

    /**
     * redis密码
     */
    private String redis_password;

    /**
     * 远端代理地址
     */
    private String proxy_remote;

    /**
     * cpu核数
     */
    private Integer cpu;

    /**
     * 内存大小（G）
     */
    private Integer memory;

    /**
     * 中止标志
     */
    private Integer stopFlag;

    /**
     * customerId
     */
    private String customerId;

    /**
     * labels
     */
    private Map<String,String> labels;

    /**
     * 参数map, 设定参数将设置在yaml的Args中
     */
    private Map<String, String> parameters;

    /**
     * 扩展参数，设定参数将将出现在yaml的Environment中
     */
    private Map<String, String> extParameters;

    /**
     * 项目id
     */
    private Integer projectId;

    /**
     * nbUser
     */
    private String nbUser;

    /**
     * 创建人
     */
    private String createUser;
}
