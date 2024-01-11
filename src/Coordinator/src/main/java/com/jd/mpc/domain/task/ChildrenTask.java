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
 * 子任务表
 *
 * @author : liuyk
 */
@SuppressWarnings("unused")
@TableName("children_task")
public class ChildrenTask {
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
     * 任务序号
     */
    @NotEmpty(message = "任务序号不能为空")
    @Digits(integer = 10, fraction = 0, message = "任务序号只能为数字")
    private Integer subId;

    /**
     * 任务序号
     */
    @NotEmpty(message = "任务序号不能为空")
    @Digits(integer = 10, fraction = 0, message = "任务序号只能为数字")
    private Integer taskIndex;

    /**
     * pod数量
     */
    @Digits(integer = 10, fraction = 0, message = "pod数量只能为数字")
    private Integer podNum;

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
    private String taskType;

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
     * 任务消息
     */
    private String message;

    /**
     * 任务结果
     */
    private String result;

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
     * getParentTaskId
     *
     * @return java.lang.String
     **/
    public String getParentTaskId() {
        return parentTaskId;
    }

    /**
     * setParentTaskId
     *
     * @param parentTaskId parentTaskId
     **/
    public void setParentTaskId(String parentTaskId) {
        this.parentTaskId = parentTaskId == null ? null : parentTaskId.trim();
    }

    /**
     * getSubId
     *
     * @return java.lang.Integer
     **/
    public Integer getSubId() {
        return subId;
    }

    /**
     * setSubId
     *
     * @param subId subId
     **/
    public void setSubId(Integer subId) {
        this.subId = subId;
    }

    /**
     * getTaskIndex
     *
     * @return java.lang.Integer
     **/
    public Integer getTaskIndex() {
        return taskIndex;
    }

    /**
     * setTaskIndex
     *
     * @param taskIndex taskIndex
     **/
    public void setTaskIndex(Integer taskIndex) {
        this.taskIndex = taskIndex;
    }

    /**
     * getPodNum
     *
     * @return java.lang.Integer
     **/
    public Integer getPodNum() {
        return podNum;
    }

    /**
     * setPodNum
     *
     * @param podNum podNum
     **/
    public void setPodNum(Integer podNum) {
        this.podNum = podNum;
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
     * getTaskType
     *
     * @return java.lang.String
     **/
    public String getTaskType() {
        return taskType;
    }

    /**
     * setTaskType
     *
     * @param taskType taskType
     **/
    public void setTaskType(String taskType) {
        this.taskType = taskType == null ? null : taskType.trim();
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

    /**
     * getMessage
     *
     * @return java.lang.String
     **/
    public String getMessage() {
        return message;
    }

    /**
     * setMessage
     *
     * @param message message
     **/
    public void setMessage(String message) {
        this.message = message == null ? null : message.trim();
    }

    /**
     * getResult
     *
     * @return java.lang.String
     **/
    public String getResult() {
        return result;
    }

    /**
     * setResult
     *
     * @param result result
     **/
    public void setResult(String result) {
        this.result = result == null ? null : result.trim();
    }
}