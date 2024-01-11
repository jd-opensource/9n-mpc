package com.jd.mpc.domain.vo;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import com.jd.mpc.domain.offline.commons.PreJob;
import lombok.Data;

/**
 * @Author: tiangengle1
 * @Date: 2022/3/17
 */
@Data
public class TaskInfo {

	/**
	 * 任务id, web端传过来的值
	 */
	@NotBlank
	private String id;

	/**
	 * 任务类型
	 */
	@NotBlank(message = "任务类型不能为空")
	private String type;

	/**
	 * 任务列表
	 */
	@NotNull(message = "任务列表不能为空")
	private List<PreJob> subTasks;
}
