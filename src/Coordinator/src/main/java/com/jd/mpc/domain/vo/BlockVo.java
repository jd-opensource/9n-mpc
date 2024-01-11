package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * 项目名称：
 * 类名称：
 * 类描述：
 * 创建时间：
 */
@Data
public class BlockVo {
    /**blockId */
    private String blockId;
    /**数据名称 */
    private String dataSourceName;
    /**样本数 */
    private int exampleNum;
}
