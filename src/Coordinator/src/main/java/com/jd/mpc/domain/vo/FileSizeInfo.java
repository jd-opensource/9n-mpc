package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * 文件信息对象
 *
 * 
 * @date 2021/11/22 6:06 下午
 */
@Data
public class FileSizeInfo {

    /**
     * 文件行数
     */
    private Long row;

    /**
     * 文件列数
     */
    private Long col;


}
