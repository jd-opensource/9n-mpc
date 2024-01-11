package com.jd.mpc.domain.vo;

import lombok.Data;

/**
 * 项目名称：
 * 类名称：
 * 类描述：
 * 创建人：
 * 创建时间：
 */
@Data
public class JoinRawInfo {
    String app_id ;
    String data_source_name;
    String train_data_start;
    String train_data_end ;
    int partition_num =0;
    float negative_sampling_rate = 0L;
}
