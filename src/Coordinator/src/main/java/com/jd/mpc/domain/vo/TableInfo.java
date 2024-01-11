package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * table 信息
 *
 * @Author: tiangengle1
 * @Date: 2022/3/22
 */
@Data
public class TableInfo {

	/**
	 * 数据名称
	 */
	private String tableName;

	/**
	 * 数据路径
	 */
	private String tablePath;

	/**
	 * 格式
	 */
	private String format;
}
