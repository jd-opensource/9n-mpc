package com.jd.mpc.domain.vo;

import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import lombok.Data;

/**
 * 
 * @Date: 2022/3/17
 */
@Data
public class SqlParserParam {

	/**
	 * 任务id
	 */
	@NotBlank(message = "任务id不能为空")
	private String id;

	/**
	 * 任务类型
	 */
	@NotBlank(message = "任务类型不能为空")
	private String type;

	/**
	 * sql
	 */
	@NotBlank(message = "sql不能为空")
	private String sql;

	/**
	 * target信息
	 */
	@NotNull
	private List<TargetInfo> targetInfos;
}
