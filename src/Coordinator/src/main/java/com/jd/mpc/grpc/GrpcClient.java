package com.jd.mpc.grpc;

import authprotocol.AuthServiceGrpc;
import fedlearner.app.CheckJoinedDataServiceGrpc;
import fedlearner.app.SchedulerGrpc;
import fedlearner.app.StateSynServiceGrpc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import lombok.Data;
import lombok.NoArgsConstructor;
import offline.OfflineServiceGrpc;
import online.OnlineServiceGrpc;
import outer.OuterServiceGrpc;
import predict.ApiServiceGrpc;

/**
 * grpc客户端
 *
 * 
 * @date 2021/9/22 2:56 下午
 */
@Data
@Component
@NoArgsConstructor
public class GrpcClient {

    private static final Metadata.Key<String> customHeadKey = Metadata.Key.of("id",
            Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> targetHeadKey = Metadata.Key.of("target",
            Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> uuidHeadKey = Metadata.Key.of("uuid",
            Metadata.ASCII_STRING_MARSHALLER);

    private ManagedChannel channel;

    private OfflineServiceGrpc.OfflineServiceBlockingStub offlineStub;

    private AuthServiceGrpc.AuthServiceBlockingStub authStub;

    private OnlineServiceGrpc.OnlineServiceBlockingStub onlineStub;

    private OuterServiceGrpc.OuterServiceBlockingStub outerStub;

    private ApiServiceGrpc.ApiServiceBlockingStub predictStub;

    private SchedulerGrpc.SchedulerBlockingStub schedulerStub;

    private StateSynServiceGrpc.StateSynServiceBlockingStub StateSynStub;

    private CheckJoinedDataServiceGrpc.CheckJoinedDataServiceBlockingStub checkDataStub;


    @Value("${grpc.proxy.host}")
    private String host;

    @Value("${grpc.proxy.port}")
    private Integer port;

    public GrpcClient(String host, int port, String type, String target) {

        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();

        Metadata metadata = new Metadata();
        if ("sign".equals(type)){
            metadata.put(customHeadKey,"auth");
        }else {
            metadata.put(customHeadKey, "coordinator");
        }
        metadata.put(targetHeadKey, target);
        switch (type) {
            case "offline":
                offlineStub = MetadataUtils
                        .attachHeaders(OfflineServiceGrpc.newBlockingStub(channel), metadata)
                        .withMaxInboundMessageSize(Integer.MAX_VALUE)
                        .withMaxOutboundMessageSize(Integer.MAX_VALUE);
                break;
            case "sign":
                authStub = MetadataUtils
                        .attachHeaders(AuthServiceGrpc.newBlockingStub(channel), metadata)
                        .withMaxInboundMessageSize(Integer.MAX_VALUE)
                        .withMaxOutboundMessageSize(Integer.MAX_VALUE);
                break;
            case "outer":
                outerStub = MetadataUtils
                        .attachHeaders(OuterServiceGrpc.newBlockingStub(channel), metadata)
                        .withMaxInboundMessageSize(Integer.MAX_VALUE)
                        .withMaxOutboundMessageSize(Integer.MAX_VALUE);
                break;
            case "scheduler":
                metadata.put(uuidHeadKey,target);
                schedulerStub = MetadataUtils
                        .attachHeaders(SchedulerGrpc.newBlockingStub(channel), metadata)
                        .withMaxInboundMessageSize(Integer.MAX_VALUE)
                        .withMaxOutboundMessageSize(Integer.MAX_VALUE);
                break;
            case "state":
                metadata.put(uuidHeadKey,target);
                StateSynStub = MetadataUtils
                        .attachHeaders(StateSynServiceGrpc.newBlockingStub(channel), metadata)
                        .withMaxInboundMessageSize(Integer.MAX_VALUE)
                        .withMaxOutboundMessageSize(Integer.MAX_VALUE);
                break;
            case "checkPoint":
                metadata.put(uuidHeadKey,target);
                checkDataStub = MetadataUtils
                        .attachHeaders(CheckJoinedDataServiceGrpc.newBlockingStub(channel), metadata)
                        .withMaxInboundMessageSize(Integer.MAX_VALUE)
                        .withMaxOutboundMessageSize(Integer.MAX_VALUE);
                break;
            case "predict":
                predictStub = MetadataUtils
                        .attachHeaders(ApiServiceGrpc.newBlockingStub(channel), metadata)
                        .withMaxInboundMessageSize(Integer.MAX_VALUE)
                        .withMaxOutboundMessageSize(Integer.MAX_VALUE);
                break;
        }

    }

    /**
     * 根据服务类型返回grpc客户端
     *
     * @param type 服务类型
     * @return grpc客户端
     */
    public GrpcClient getClient(String type, String target) {
        return new GrpcClient(host, port, type, target);
    }
    /**
     * 根据ip+port返回grpc客户端
     *
     * @return grpc客户端
     */
    public GrpcClient getClient(String ip,int port, String target) {
        return new GrpcClient(host, port, "eaClient", target);
    }

    public GrpcClient getSchedulerClient(String host, int port, String target) {
        return new GrpcClient(host, port, "scheduler", target);
    }
    public GrpcClient getEaStateClient(String host, int port, String target) {
        return new GrpcClient(host, port, "state", target);
    }
    public GrpcClient getEaCheckJoinedDataClient(String host, int port, String target) {
        return new GrpcClient(host, port, "checkPoint", target);
    }

}
