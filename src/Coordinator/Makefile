export JAVA_HOME=/usr

jarName=mpc-coordinator-1.0-SNAPSHOT.jar
ifeq ($(FLAG),jcjd)
	# pre1
	imageName=jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coordinator_pk_release
	restartScriptPath=/Users/feiguodong1/Documents/project/k8/k8shell/pre1_restart_pod.sh
endif
ifeq ($(FLAG),fljd)
	# 9nmpc-fl
	imageName=jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coodinator_jttest
	restartScriptPath=/Users/feiguodong1/Documents/project/k8/k8shell/fl_restart_pod.sh
endif
ifeq ($(FLAG),pejd)
	# pre
	imageName=jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coodinator_pre
	restartScriptPath=1.sh
endif
#ifeq ($(FLAG),oljd)
#	# mpc-online
#	imageName=jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coordinator_master
#endif
ifeq ($(FLAG),prodjd)
	# mpc-online
	imageName=jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coordinator_prod_v1.0.0
	restartScriptPath=111.sh
endif
ifeq ($(FLAG),tejd)
	imageName=jd-mpc-cn-north-1-inner.jcr.service.jdcloud.com/mpcimage/9ntrain:coordinator_test_v1.0.0
#	imageName=yd-model-learning-cn-north-1.jcr.service.jdcloud.com/yd-model-store:coordinator_highly_available
	restartScriptPath=111.sh
endif
ifeq ($(FLAG),tjd)
	imageName=test.registry.net/9ntrain:coodinator_pre
	restartScriptPath=/Users/feiguodong1/Documents/project/k8/k8shell/tjd_restart_pod.sh
endif
ifeq ($(FLAG),btjd)
	imageName=test.registry.net/9ntrain:coodinator_busi_test
	restartScriptPath=tmp.sh
endif
ifndef imageName
	return -1
endif
ifndef restartScriptPath
	return -1
endif

restart: push
	sh $(restartScriptPath) coordinator

push: image
	docker push $(imageName)

image: compile
	- rm -rf docker/$(jarName)
	- docker rmi $(imageName)
	cp target/$(jarName) docker/$(jarName)
	cd docker && docker build -f Dockerfile.jre8 -t $(imageName) .

compile:
	/Users/chenghekai1/Desktop/software/apache-maven-3.8.6/bin/mvn clean package -Dmaven.test.skip=true