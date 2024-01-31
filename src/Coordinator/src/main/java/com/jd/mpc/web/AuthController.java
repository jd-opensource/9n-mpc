package com.jd.mpc.web;

import cn.hutool.core.codec.Base64;
import com.alibaba.fastjson.JSONObject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.jd.mpc.common.response.CommonResponse;
import com.jd.mpc.common.util.JNISigner;
import com.jd.mpc.domain.param.WorkerInfoParam;
import com.jd.mpc.domain.vo.AuthInfoDto;
import com.jd.mpc.domain.vo.VerifyVo;
import com.jd.mpc.grpc.GrpcSignClient;
import com.jd.mpc.service.AuthInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import authprotocol.GrpcVo;
import authprotocol.VerifyGrpcVo;

import jakarta.annotation.Resource;

/**
 * @Description: auth
 * 
 * @Date: 2022/10/12
 */
@Slf4j
@RestController
@RequestMapping("auth")
public class AuthController {
    @Resource
    private AuthInfoDto authInfoDto;
    @Resource
    private GrpcSignClient signClient;
    /**
     * 验签
     * @param workerInfo
     * @return
     */
    @PostMapping("verify")
    public CommonResponse<VerifyVo> verify(@RequestBody WorkerInfoParam workerInfo) throws InvalidProtocolBufferException {
        //1.sign
        String dataStr = JSONObject.toJSONString(workerInfo);
        byte[] sig = JNISigner.sign(authInfoDto.getPriKey(), dataStr.getBytes());
        GrpcVo grpcVo = signClient.verify(authInfoDto.getCert(), Base64.encode(sig), dataStr);
        VerifyVo verifyVo = new VerifyVo();
        if (grpcVo.getStatus() == 0){
            VerifyGrpcVo verifyGrpcVo = VerifyGrpcVo.parseFrom(grpcVo.getResult());
            verifyVo.setCert(verifyGrpcVo.getCert());
            verifyVo.setData(verifyGrpcVo.getData());
            verifyVo.setSig(verifyGrpcVo.getSig());
            return CommonResponse.ok(verifyVo);
        }else {
            return CommonResponse.fail(grpcVo.getStatus(),grpcVo.getErrMsg());
        }
    }
}
