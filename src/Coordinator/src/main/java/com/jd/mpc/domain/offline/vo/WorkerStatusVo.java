package com.jd.mpc.domain.offline.vo;

import com.jd.mpc.domain.offline.commons.OfflineTask;
import lombok.Data;

import java.util.List;

@Data
public class WorkerStatusVo {

    private String uuid;//AppID_Service_index

    private int status; //-1 运行中  0 完成 大于0 错误

    private String msg;

    private String result ;

}
