package com.jd.mpc.domain.offline.commons;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author luoyuyufei1
 * @date 2022/1/11 2:38 下午
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Job {

    private String id;

    private String type;

    private List<SubTask> subTaskList;
}
