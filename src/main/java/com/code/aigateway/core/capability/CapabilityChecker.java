package com.code.aigateway.core.capability;

import com.code.aigateway.core.model.UnifiedRequest;
import com.code.aigateway.core.router.RouteResult;

/**
 * 能力检查器
 */
public interface CapabilityChecker {
    void validate(UnifiedRequest request, RouteResult routeResult);
}
