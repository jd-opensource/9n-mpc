package com.jd.mpc.grpc;

import org.springframework.stereotype.Component;
import predict.FeaturesListProto;
import predict.Request;
import predict.Response;

import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * 调用在线预测worker Grpc服务
 *
 * @author luoyuyufei1
 * @date 2021/9/26 10:30 下午
 */
@Component
public class GrpcPredictClient {

    /**
     * 查询特征work信息
     *
     * @param host ip
     * @param port 端口
     * @return 特征work信息
     */
    public String predict(String data, String host, int port) {
        try {
            GrpcClient client = new GrpcClient(host, port, "predict", "");
            byte[] bytes = Base64.getDecoder().decode(data);
            Request request = Request.newBuilder().setExamples(FeaturesListProto.parseFrom(bytes)).build();
            Response response = client.getPredictStub().predict(request);
            this.closeChannel(client);
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "调用失败";
    }

    private void closeChannel(GrpcClient client) {
        try {
            client.getChannel().shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
