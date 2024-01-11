package com.jd.mpc.web;

import com.jd.mpc.common.response.CommonResponse;
import com.jd.mpc.domain.vo.TaskStatusInfo;
import com.jd.mpc.grpc.GrpcOfflineClient;
import com.jd.mpc.redis.RedisService;
import com.jd.mpc.service.K8sService;
import com.jd.mpc.service.OfflineService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 任务接口层
 *
 * @author luoyuyufei1
 * @date 2021/9/22 6:26 下午
 */
@Slf4j
@RestController
public class OfflineController {

    @Resource
    private OfflineService offlineService;

    @Resource
    private GrpcOfflineClient grpcOfflineClient;

    @Resource
    private K8sService k8sService;
    @Resource
    private RedisService redisService;
    private String appId;


    @PostMapping(value = "/coordinator/offline/test/grpc")
    public CommonResponse<String> testGrpc(@RequestParam String targets,@RequestParam Boolean isCloseChannel,@RequestParam Integer count,@RequestParam(defaultValue = "1") Integer threadCount,@RequestBody String body) {
        return CommonResponse.ok(grpcOfflineClient.test(targets,isCloseChannel,count,threadCount,body));
    }


    /**
     * 解析json并提交任务  【离线】
     *
     * @param preJson 任务json
     * @return 是否成功
     */
    @RequestMapping(value = "/coordinator/offline/commit", method = RequestMethod.POST)
    public CommonResponse<Boolean> commitTask(@RequestBody String preJson) {

        return CommonResponse.ok(offlineService.commitTask(preJson));
    }

    /**
     * 停止任务  【离线】
     *
     * @param id 任务id
     * @return 是否成功
     */
    @RequestMapping(value = "/coordinator/offline/stop", method = RequestMethod.POST)
    public CommonResponse<Boolean> stopTask(@RequestParam String id, @RequestParam String customerId,@RequestParam(required = false) String target) {
        return CommonResponse.ok(offlineService.stopTask(id,customerId,target));
    }

    /**
     * 查询任务状态和进度  【离线】
     *
     * @param id          任务id
     * @param customerIds 任务端id列表
     * @return 是否成功
     */
    @RequestMapping(value = "/coordinator/offline/query", method = RequestMethod.GET)
    public CommonResponse<List<TaskStatusInfo>> queryTask(@RequestParam String id, @RequestParam String customerIds,@RequestParam(required = false)String targets) {
        return CommonResponse.ok(offlineService.queryTask(id, customerIds,targets));
    }

    /**
     * 查询任务状态和进度  【离线】
     *
     * @param id          任务id
     * @return 是否成功
     */
    @RequestMapping(value = "/coordinator/offline/taskInfo/query", method = RequestMethod.GET)
    public CommonResponse<String> queryTaskInfo(@RequestParam String id,@RequestParam boolean isDb) {
        return CommonResponse.ok(offlineService.queryTaskInfo(id,isDb));
    }


	/**
	 * 回调提交后续任务【离线】
	 */
	@PostMapping(value = "/coordinator/offline/callback")
	public CommonResponse<Boolean> callback(@RequestBody @Validated String taskInfo) {
		return CommonResponse.ok(offlineService.callback(taskInfo));
	}


}
