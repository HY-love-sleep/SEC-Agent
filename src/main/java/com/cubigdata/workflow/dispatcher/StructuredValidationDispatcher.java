package com.cubigdata.workflow.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/9/25 10:46
 */
public class StructuredValidationDispatcher implements EdgeAction {
    @Override
    public String apply(OverAllState state) {
        return state.value("is_validate")
                .map(v -> v.equals(0) ? "no" : "yes")
                .orElse("no");
    }
}
