package com.code.aigateway.core.router;

import com.code.aigateway.core.model.UnifiedRequest;

public interface ModelRouter {
    RouteResult route(UnifiedRequest request);
}
