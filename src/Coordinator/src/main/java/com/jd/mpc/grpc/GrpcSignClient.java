package com.jd.mpc.grpc;

import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import authprotocol.GrpcVo;
import authprotocol.IssueGrpcParam;
import authprotocol.VerifyGrpcParam;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * 调用jd Grpc服务
 *
 * @author luoyuyufei1
 * @date 2021/9/26 10:30 下午
 */
@Component
@Slf4j
public class GrpcSignClient {

    @Resource
    private GrpcClient grpcClient;

    public GrpcVo verify(String cert,String sig,String data){
        VerifyGrpcParam verifySignReq = VerifyGrpcParam.newBuilder()
                .setCert(cert)
                .setData(data)
                .setSig(sig)
                .build();
        GrpcClient client = grpcClient.getClient("sign", "9n_demo_1");
        GrpcVo vo = client.getAuthStub().verify(verifySignReq);
        this.closeChannel(client);
        return vo;
    }

    public GrpcVo issueCert(String commonName,String organization,byte[] key){
        IssueGrpcParam param = IssueGrpcParam.newBuilder()
                .setCommonName(commonName)
                .setOrganization(organization)
                .setKey(ByteString.copyFrom(key))
                .build();
        GrpcClient client = grpcClient.getClient("sign", "9n_demo_1");
        GrpcVo vo = client.getAuthStub().issueCert(param);
        this.closeChannel(client);
        return vo;
    }

    private void closeChannel(GrpcClient client) {
        try {
            client.getChannel().shutdown().awaitTermination(60, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
