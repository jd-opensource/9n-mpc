FROM jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:jni_sign as jni_sign

# reference https://docs.spring.io/spring-boot/docs/2.5.5/reference/htmlsingle/#features.container-images
FROM jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coor_base AS builder
ARG HOME=/home
WORKDIR $HOME
COPY mpc-coordinator-1.0-SNAPSHOT.jar mpc-coordinator-1.0-SNAPSHOT.jar
RUN java -Djarmode=layertools -jar mpc-coordinator-1.0-SNAPSHOT.jar extract

FROM jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coor_base
ARG HOME=/home
WORKDIR $HOME
COPY --from=builder $HOME/dependencies/ ./
COPY --from=builder $HOME/spring-boot-loader/ ./
COPY --from=builder $HOME/snapshot-dependencies/ ./
COPY --from=builder $HOME/application/ ./
COPY --from=jni_sign /usr/local/lib/libjni_sign.so /usr/lib/libjni_sign.so
ADD start.sh $HOME/start.sh
CMD ["sh", "start.sh"]









