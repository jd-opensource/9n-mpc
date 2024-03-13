package com.jd.mpc.domain.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 文件信息对象
 *
 * 
 * @date 2021/11/22 6:06 下午
 */
@Data
@Builder
public class FileSchemaInfo {

    /**
     * 列名
     */
    private String colName;

    /**
     * 列类型
     */
    private String dataType;


}
