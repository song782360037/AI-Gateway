package com.code.aigateway.core.runtime;

import com.code.aigateway.admin.service.RuntimeConfigRefreshService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动后缓存预热执行器
 *
 * <p>优先从 MySQL 加载运行时配置并构建本地快照；
 * 若失败则记录日志，后续由 YAML fallback 兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements ApplicationRunner {

    private final RuntimeConfigRefreshService runtimeConfigRefreshService;

    @Override
    public void run(ApplicationArguments args) {
        boolean success = runtimeConfigRefreshService.reloadFromDb("startup");
        if (success) {
            log.info("[运行时配置预热] 启动预热成功");
            return;
        }

        log.warn("[运行时配置预热] 启动预热失败，将继续使用 YAML fallback 路由");
    }
}
