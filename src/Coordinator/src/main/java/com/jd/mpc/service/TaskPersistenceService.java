package com.jd.mpc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.jd.mpc.common.enums.IsDeletedEnum;
import com.jd.mpc.common.enums.TaskStatusEnum;
import com.jd.mpc.common.response.CommonException;
import com.jd.mpc.common.response.ErrorStatus;
import com.jd.mpc.domain.offline.commons.OfflineTask;
import com.jd.mpc.domain.offline.commons.SubTask;
import com.jd.mpc.domain.task.*;
import com.jd.mpc.mapper.ChildrenTaskMapper;
import com.jd.mpc.mapper.ParentTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 离线任务持久化服务
 *
 * 
 * @date 2021/11/1 11:23 上午
 */
@Component
@Slf4j
public class TaskPersistenceService {

    @Resource
    private ParentTaskMapper parentTaskMapper;

    @Resource
    private ChildrenTaskMapper childrenTaskMapper;

    /**
     * 根据任务id查询父任务
     *
     * @param taskId 任务id
     * @return 父任务
     */
    public ParentTask getByTaskId(String taskId) {
        if (taskId == null) {
            throw new CommonException(ErrorStatus.PARAMETER_EMPTY, "任务id不能为空");
        }
        LambdaQueryWrapper<ParentTask> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ParentTask::getTaskId, taskId);
        return parentTaskMapper.selectOne(wrapper);
    }

    /**
     * 根据任务id查询父任务
     *
     * @param taskId 任务id
     * @return 子任务列表
     */
    public List<ChildrenTask> getChildrenTaskList(String taskId) {
        if (taskId == null) {
            throw new CommonException(ErrorStatus.PARAMETER_EMPTY, "任务id不能为空");
        }
        LambdaQueryWrapper<ChildrenTask> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(ChildrenTask::getParentTaskId, taskId);
        return childrenTaskMapper.selectList(wrapper);
    }

    /**
     * 插入父子全部任务
     *
     * @param type     父任务
     * @param taskList 任务列表
     */
    @Transactional
    public void insertAllTask(String type, List<SubTask> taskList, String preJson) {
        SubTask task = taskList.get(0);
        //插入父任务
        ParentTask parentTask = new ParentTask();
        parentTask.setTaskId(task.getId());
        parentTask.setStatus(TaskStatusEnum.NEW.getStatus());
        parentTask.setType(type);
        parentTask.setCreateAt(LocalDateTime.now());
        parentTask.setUpdateAt(LocalDateTime.now());
        parentTask.setIsDeleted(IsDeletedEnum.FALSE.getStatus());
        parentTask.setParams(preJson);
        parentTaskMapper.insert(parentTask);
        //批量插入子任务
        this.insertChildrenTask(taskList);
    }

    /**
     * 批量插入子任务
     *
     * @param taskList 任务列表
     */
    @Transactional
    public void insertChildrenTask(List<SubTask> taskList) {
        List<ChildrenTask> list = taskList.stream()
                .map(SubTask::getTasks)
                .flatMap(Collection::stream)
                .map(this::createChildrenTask)
                .collect(Collectors.toList());
        list.forEach(x -> childrenTaskMapper.insert(x));
    }

    /**
     * 根据任务id 更新父任务状态
     *
     * @param id     任务id
     * @param status 状态
     */
    @Transactional
    public void updateParentTaskStatus(String id, Integer status) {
        LambdaUpdateWrapper<ParentTask> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper
                .set(ParentTask::getStatus,status)
                .set(ParentTask::getUpdateAt,LocalDateTime.now())
                .eq(ParentTask::getTaskId, id);
        parentTaskMapper.update(null,updateWrapper);
    }

    /**
     * 根据任务id 更新子任务状态
     *
     * @param parentId  父任务id
     * @param subId     子任务序号
     * @param taskIndex 子任务序号
     * @param status    状态
     */
    @Transactional
    public void updateChildrenTaskStatus(String parentId, Integer subId, Integer taskIndex, Integer status) {
        LambdaUpdateWrapper<ChildrenTask> wrapper = Wrappers.lambdaUpdate();
        wrapper
                .set(ChildrenTask::getStatus,status)
                .set(ChildrenTask::getUpdateAt,LocalDateTime.now())
                .eq(ChildrenTask::getParentTaskId, parentId)
                .eq(ChildrenTask::getSubId, subId)
                .eq(ChildrenTask::getTaskIndex, taskIndex);
        childrenTaskMapper.update(null,wrapper);
    }

    /**
     * 根据任务id 更新子任务状态
     *
     * @param parentId  父任务id
     * @param subId     子任务序号
     * @param taskIndex 子任务序号
     * @param message   消息
     * @param result    结果
     * @param status    状态
     */
    @Transactional
    public void updateChildrenTaskResult(String parentId, Integer subId, Integer taskIndex, String message, String result, Integer status) {
        LambdaUpdateWrapper<ChildrenTask> wrapper = Wrappers.lambdaUpdate();
        wrapper
                .set(ChildrenTask::getMessage,message)
                .set(ChildrenTask::getResult,result)
                .set(ChildrenTask::getStatus,status)
                .set(ChildrenTask::getUpdateAt,LocalDateTime.now())
                .eq(ChildrenTask::getParentTaskId, parentId)
                .eq(ChildrenTask::getSubId, subId)
                .eq(ChildrenTask::getTaskIndex, taskIndex);
        childrenTaskMapper.update(null,wrapper);
    }

    /**
     * 创建子任务
     *
     * @param offlineTask 任务
     * @return 子任务
     */
    private ChildrenTask createChildrenTask(OfflineTask offlineTask) {
        ChildrenTask childrenTask = new ChildrenTask();
        childrenTask.setParentTaskId(offlineTask.getId());
        childrenTask.setSubId(offlineTask.getSubId());
        childrenTask.setTaskIndex(offlineTask.getTaskIndex());
        childrenTask.setTaskType(offlineTask.getTaskType());
        childrenTask.setPodNum(offlineTask.getPodNum());
        childrenTask.setStatus(TaskStatusEnum.NEW.getStatus());
        childrenTask.setIsDeleted(IsDeletedEnum.FALSE.getStatus());
        childrenTask.setCreateAt(LocalDateTime.now());
        childrenTask.setUpdateAt(LocalDateTime.now());
        return childrenTask;
    }
}

