package com.jd.mpc.domain.form;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@Builder
@AllArgsConstructor
public class EsQueryForm {
    @JSONField(name = "query")
    private JSONObject query;
    @JSONField(name = "size")
    private Integer size;
    @JSONField(name = "from")
    private Integer from;
}
