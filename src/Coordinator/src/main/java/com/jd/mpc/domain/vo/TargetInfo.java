package com.jd.mpc.domain.vo;

import java.util.List;

import lombok.Data;

/**
 * target 信息
 *
 * 
 * @Date: 2022/3/22
 */
@Data
public class TargetInfo {

	/**
	 * customerId
	 */
	private String target;

	/**
	 * leader / follower
	 */
	private String role;

	/**
	 * 输出路径
	 */
	private String outputPath;

	/**
	 * 表信息
	 */
	private List<TableInfo> tableInfos;
}
