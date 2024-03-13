package com.jd.mpc.domain.task;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 父任务表
 *
 * 
 */
@SuppressWarnings("unused")
@TableName("parent_task")
public class ParentTask {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务id
     */
    @NotBlank(message = "任务id不能为空")
    @Size(max = 100, message = "任务id不能超过100位")
    private String taskId;

    /**
     * 任务状态
     */
    @NotEmpty(message = "任务状态不能为空")
    @Digits(integer = 10, fraction = 0, message = "任务状态只能为数字")
    private Integer status;

    /**
     * 任务类型
     */
    @Size(max = 100, message = "任务类型不能超过100位")
    private String type;

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

    /**
     * 原始参数
     */
    private String params;

    /**
     * getId
     *
     * @return java.lang.Long
     **/
    public Long getId() {
        return id;
    }

    /**
     * setId
     *
     * @param id id
     **/
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * getTaskId
     *
     * @return java.lang.String
     **/
    public String getTaskId() {
        return taskId;
    }

    /**
     * setTaskId
     *
     * @param taskId taskId
     **/
    public void setTaskId(String taskId) {
        this.taskId = taskId == null ? null : taskId.trim();
    }

    /**
     * getStatus
     *
     * @return java.lang.Integer
     **/
    public Integer getStatus() {
        return status;
    }

    /**
     * setStatus
     *
     * @param status status
     **/
    public void setStatus(Integer status) {
        this.status = status;
    }

    /**
     * getType
     *
     * @return java.lang.String
     **/
    public String getType() {
        return type;
    }

    /**
     * setType
     *
     * @param type type
     **/
    public void setType(String type) {
        this.type = type == null ? null : type.trim();
    }

    /**
     * getCreateAt
     *
     * @return java.time.LocalDateTime
     **/
    public LocalDateTime getCreateAt() {
        return createAt;
    }

    /**
     * setCreateAt
     *
     * @param createAt createAt
     **/
    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    /**
     * getUpdateAt
     *
     * @return java.time.LocalDateTime
     **/
    public LocalDateTime getUpdateAt() {
        return updateAt;
    }

    /**
     * setUpdateAt
     *
     * @param updateAt updateAt
     **/
    public void setUpdateAt(LocalDateTime updateAt) {
        this.updateAt = updateAt;
    }

    /**
     * getIsDeleted
     *
     * @return java.lang.Byte
     **/
    public Byte getIsDeleted() {
        return isDeleted;
    }

    /**
     * setIsDeleted
     *
     * @param isDeleted isDeleted
     **/
    public void setIsDeleted(Byte isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }
}