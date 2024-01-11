package com.jd.mpc.common.enums;

import java.util.Objects;

import lombok.Getter;

/**
 * 任务类型枚举
 *
 * @author luoyuyufei1
 * @date 2021/9/26 8:48 下午
 */
@Getter
public enum TaskTypeEnum {

    /**
     * psi任务
     */
    PSI("psi"),

    /**
     * local任务x
     */
    LOCAL("local"),

    /**
     * callback任务
     */
    CALLBACK("callback"),

    /**
     * 特征任务
     */
    FEATURE("feature"),

    /**
     * 横向特征任务
     */
    FEATURE_FL("feature-fl"),

    /**
     * 训练任务
     */
    TRAIN("train"),

    /**
     * 联通任务
     */
    UNICOM("unicom"),

    /**
     * 京享值
     */
    JXZ("jxz"),

    /**
     * lr任务
     */
    LR("lr"),

    /**
     * shapley-value任务
     */
    SHAPLEY_VALUE("shapley-value"),

    /**
     * 横向联邦
     */
    HRZ_FL("hrz-fl"),
    /**
     * 预测
     */
    PREDICT("predict"),
    /**
     * tree 分布式树模型
     */
    TREE_XGB("tree"),

    /**
     * 增加随机森林
     */
    TREE_TRAIN_RF("tree-train-rf"),

    /**
     * XGBoost模型
     */
    XGBOOST("xgboost"),
    /**
     * 横向预测
     */
    HRZ_FL_PREDICT("hrz-fl-predict"),
    /**
     * 数据拆分
     */
    CUT_DATAFRAME("cut-dataframe"),

    /**
     * 纵向NN
     */
    NN("nn"),

    /**
     * vif
     */
    VIF("FilterVIF"),
    /**
     * 样本稳定性系数
     */
    STABILITY_INDEX("stability-index"),

    /**
     * local_sql
     */
    LOCAL_SQL("local-sql"),

    /**
     * local_mask
     */
    LOCAL_MASK("local-mask"),

    /**
     * plumber
     */
    PLUMBER("plumber"),

    /**
     * mpc
     */
    MPC("mpc"),

    /**
     * 分布式psi
     */
    NEW_PSI("new-psi"),

    /**
     * 线性评估
     */
    LINEAR_EVALUATE("linear-evaluate"),
    /**
     * nn评估
     */
    NN_EVALUATE("nn-evaluate"),
    /**
     * nn所用的mpc
     */
    NN_MPC("nn-mpc"),
    /**
     * 评分卡
     */
    SCORE_CARD("score-card"),

    /**
     * 京享值
     */
    JOYV("joyv"),
    /**
     * spearmanmpc
     */
    SPEARMANMPC("spearmanmpc"),

    JTPSI("jtpsi"),

    /**
     * etl
     */
    ETL("etl"),
    FILE_SERVICE("file-service"),
    LOCAL_WORKER("local-worker"),
    FILE_TRANSFER("file-transfer"),
    BUFFALO_WORKER("buffalo-worker"),

    BDP_DECRYPT("bdp-decrypt"),

    BUSI_DOWNLOAD("busi-download")
    ;

    private final String name;

    TaskTypeEnum(String name) {
        this.name = name;
    }

    public static TaskTypeEnum getByValue(String name) {
        for (TaskTypeEnum taskTypeEnum : values()) {
            if (Objects.equals(taskTypeEnum.name, name)) {
                return taskTypeEnum;
            }
        }
        return null;
    }
}
