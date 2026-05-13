package com.code.aigateway.admin.model.rsp;

import com.code.aigateway.core.router.auto.AutoRequestProfile;
import com.code.aigateway.core.router.auto.AutoRouteScore;
import lombok.Data;

import java.util.List;

/**
 * Auto 智能路由评估响应对象
 */
@Data
public class AutoRouteEvaluateRsp {

    private String routeKey;

    private AutoRequestProfile profile;

    private List<CandidateEvaluation> candidates;

    @Data
    public static class CandidateEvaluation {

        private String providerCode;

        private String targetModel;

        private boolean eligible;

        private String rejectReason;

        private AutoRouteScore score;

        private Integer priority;

        private Integer weight;

        private Integer rank;
    }
}
