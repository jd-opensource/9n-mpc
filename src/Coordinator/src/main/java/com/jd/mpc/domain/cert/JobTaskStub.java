package com.jd.mpc.domain.cert;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 
 * @date 2022-04-01 18:20
 * 任务存根，包括任务信息以及发起方和执行方签名
 */
@SuppressWarnings("unused")
@TableName("job_task_stub")
@Data
public class JobTaskStub {
    /**
     * 主键[自增]
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 任务id
     */
    @NotBlank(message = "任务id不能为空")
    @Size(max = 100, message = "任务id不能超过100位")
    private String parentTaskId;
    /**
     * 任务详情，被签名的内容
     */
    @NotBlank(message = "子任务详情")
    private String preJobJson;
    /**
     * 任务执行方
     */
    private String jobTarget;
    /**
     * 任务发起方签名
     */
    private String jobDistributorSign;
    /**
     * 任务执行方签名
     */
    private String jobExecutorSign;
    /**
     * 任务发起方证书
     */
    private String jobDistributorCert;
    /**
     * 任务执行方证书
     */
    private String jobExecutorCert;
    /**
     * 任务发起方，1=发起方为自身，0=发起方为外部节点
     */
    private Byte isLocal;
    /**
     * 创建时间
     */
    private LocalDateTime createAt;

    /**
     * 更新时间
     */
    private LocalDateTime updateAt;

    /**
     * 是否删除
     */
    private Byte isDeleted;

}
