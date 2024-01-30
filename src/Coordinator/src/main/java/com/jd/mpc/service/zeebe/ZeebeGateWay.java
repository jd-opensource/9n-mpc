package com.jd.mpc.service.zeebe;

import com.jd.mpc.common.enums.TaskTypeEnum;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.ZeebeWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Description: 融合分析算子化,类似与mvc中的controller层
 * ZeebeGateWay->ZeebeDispatcher->IZeebeService->***ZeebeService
 * 
 * @Date: 2022/10/13
 */
@Slf4j
@Service
public class ZeebeGateWay {

//    @Resource
//    private ZeebeDispatcher dispatcher;
//
//    /**
//     * - `InputVariable_id`: 任务ID
//     * - `InputVariable_input_path`：输入文件路径
//     * - `InputVariable_input_col`：求交列设置
//     * - `InputVariable_output_path` ：输出文件路径
//     * - `InputVariable_send_back` ：非leader是否可以得到输出结果
//     * - `InputVariable_participants` ：参与方列表，各方需要完全一致，第一个是leader
//     * - `InputVariable_extra_configs` ：其他可选输入参数
//     * @param client
//     * @param job
//     */
//    @ZeebeWorker(type ="${target};psi")
//    public void handlePSI(final JobClient client, final ActivatedJob job) {
//        dispatcher.doDispatch(client,job,TaskTypeEnum.PSI);
//    }
//
//    /**
//     * - `InputVariable_id` ：任务ID
//     * - `InputVariable_script_path`：任务脚本路径
//     * - `InputVariable_args`：任务脚本参数
//     * - `InputVariable_interpreter`：任务解释器
//     * @param client
//     * @param job
//     */
//    @ZeebeWorker(type ="${target};local-worker")
//    public void handleLocalWorker(final JobClient client, final ActivatedJob job) {
//        dispatcher.doDispatch(client,job,TaskTypeEnum.LOCAL_WORKER);
//    }
//
//    /**
//     * - `InputVariable_id` ：任务ID
//     * - `InputVariable_target`：对侧Domain名称
//     * - `InputVariable_mode`：收发模式[sender/receiver]
//     * - `InputVariable_path`：发送或接收文件路径
//     * @param client
//     * @param job
//     */
//    @ZeebeWorker(type ="${target};transfer")
//    public void handleTransfer(final JobClient client, final ActivatedJob job) {
//        dispatcher.doDispatch(client,job,TaskTypeEnum.FILE_TRANSFER);
//    }
//
//    /**
//     * call buffalo
//     * @param client
//     * @param job
//     */
//    @ZeebeWorker(type = "${target};buffalo")
//    public void handleBuffalo(final JobClient client, final ActivatedJob job){
//        dispatcher.doDispatch(client,job,TaskTypeEnum.BUFFALO_WORKER);
//    }
//
//    /**
//     * - `InputVariable_runID`: process runID
//     * @param client
//     * @param job
//     */
//    @ZeebeWorker(type = "${target};error-cleaner")
//    public void handleErrorCleaner(final JobClient client, final ActivatedJob job){
//        dispatcher.handleErrorCleaner(client,job);
//    }
//
//    /**
//     * send http request
//     * @param client
//     * @param job
//     */
//    @ZeebeWorker(type = "${target};http-json-client")
//    public void handleHttpCall(final JobClient client, final ActivatedJob job){
//        dispatcher.handleHttpCall(client,job);
//    }

}
