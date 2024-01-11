package com.jd.mpc.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * @author luoyuyufei1
 * @date 2022/1/12 8:28 下午
 */
@Data
public class SyncRequest {

    private List<SyncInfo> infoList;
}
