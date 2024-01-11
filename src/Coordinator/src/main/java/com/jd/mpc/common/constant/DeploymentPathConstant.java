package com.jd.mpc.common.constant;

/**
 * @Description: 部署文件路径常量
 * 
 * @Date: 2022/2/10
 */
public interface DeploymentPathConstant {
    String INTERSECTION = "intersection.yaml";

    String PSI = "psi.yaml";

    String FEATURE = "feature.yaml";

    String FEATURE_FL = "feature-fl.yaml";

    String TRAIN = "train.yaml";

    String JXZ_MPC = "jxz-mpc.yaml";

    String JXZ_LOCAL = "jxz-local.yaml";

    String UNICOM = "unicom.yaml";

    String HRZ_FL_BASE = "hrz-fl-base.yaml";

    String TRAIN_BASE = "train-base.yaml";

    String TRAIN_BASE_VIF = "train-base-vif.yaml";

    String PREDICT_VERTICAL = "predict-vertical.yaml";

    String PREDICT_HORIZONTAL = "predict-horizontal.yaml";

    String PREDICT_NN = "predict-nn.yaml";

    String PREDICT_EVAL = "predict-estimate.yaml";

    /**
     * XGB树模型
     */
    String TREE_TRAIN_XGB = "tree-train-base.yaml";

    /**
     * 增加随机森林树模型
     */
    String TREE_TRAIN_RF = "tree-train-rf.yaml";

    String HRZ_FL_PREDICT_BASE = "hrz-fl-predict-base.yaml";

    String CUT_DATAFRAME_BASE = "feature-cut-dataframe-base.yaml";

    String XGBOOST_TRAIN_BASE = "xgboost-train-base.yaml";

    String XGBBOOST_TRAIN_BASE_WITH_RAYGBO = "ray_cluster_xgb.yaml";

    String RAY_BASE = "ray_cluster.yaml";

    String NN_DC = "nn-dc.yaml";

    String NN_MPC = "mpc-nn-worker.yaml";

    String CODE_MPC = "mpc-code-worker.yaml";

    String NN_TRAINER = "nn-trainer.yaml";

    String STABILITY_INDEX = "stability-index.yaml";

    String PLUMBER_BASE = "plumber.yaml";

    String NEW_PSI = "new-psi.yaml";

    String LINEAR_EVALUATE = "linear-evaluate.yaml";

    String NN_EVALUATE = "nn-evaluate.yaml";

    String SHAPLEY_VALUE_EVALUATE = "shapley-value-evaluate.yaml";

    String SCORE_CARD = "score-card.yaml";

    String SPEARMANMPC = "spearman-mpc.yaml";

    String JTPSI_MASTER = "jingteng-master-psi.yaml";

    String JTPSI_WORKER = "jingteng-worker-psi.yaml";

    String ETL = "etl.yaml";

    String FILE_SERVICE = "file-service.yaml";

    String LOCAL_WORKER = "local-worker.yaml";

    String FILE_TRANSFER = "file-transfer.yaml";

    String BUFFALO_WORKER = "buffalo-worker.yaml";

    String BDP_DECRYPT = "bdp-decrypt.yaml";

    String BUSI_DOWNLOAD = "busi-download.yaml";

}
