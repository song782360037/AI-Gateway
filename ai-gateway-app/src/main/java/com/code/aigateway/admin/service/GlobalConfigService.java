package com.code.aigateway.admin.service;

import com.code.aigateway.admin.mapper.GlobalConfigMapper;
import com.code.aigateway.admin.model.dataobject.GlobalConfigDO;
import com.code.aigateway.admin.model.req.GlobalCustomHeadersUpdateReq;
import com.code.aigateway.admin.model.rsp.GlobalCustomHeadersRsp;
import com.code.aigateway.common.exception.BizException;
import com.code.aigateway.common.util.CustomHeaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 全局配置管理服务
 *
 * <p>提供全局自定义请求头的查询和更新能力。
 * 全局请求头对所有提供商生效，提供商级别同名头可覆盖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalConfigService {

    /** 全局自定义请求头的配置键 */
    private static final String CUSTOM_HEADERS_KEY = "custom_headers";

    private final GlobalConfigMapper globalConfigMapper;
    private final RuntimeConfigRefreshService runtimeConfigRefreshService;

    /**
     * 获取全局自定义请求头
     */
    public GlobalCustomHeadersRsp getCustomHeaders() {
        GlobalConfigDO record = globalConfigMapper.selectByConfigKey(CUSTOM_HEADERS_KEY);
        if (record == null) {
            // 不存在时返回空对象
            GlobalCustomHeadersRsp rsp = new GlobalCustomHeadersRsp();
            rsp.setCustomHeaders(Map.of());
            rsp.setVersionNo(0L);
            return rsp;
        }

        GlobalCustomHeadersRsp rsp = new GlobalCustomHeadersRsp();
        rsp.setCustomHeaders(CustomHeaderUtils.parseHeadersJson(record.getConfigValue()));
        rsp.setVersionNo(record.getVersionNo());
        return rsp;
    }

    /**
     * 更新全局自定义请求头
     */
    public void updateCustomHeaders(GlobalCustomHeadersUpdateReq req) {
        // 校验请求头中不包含受保护的认证头且值无 CRLF
        CustomHeaderUtils.validateCustomHeaders(req.getCustomHeaders());

        GlobalConfigDO record = new GlobalConfigDO();
        record.setConfigKey(CUSTOM_HEADERS_KEY);
        record.setVersionNo(req.getVersionNo());
        record.setConfigValue(CustomHeaderUtils.serializeHeadersToJson(req.getCustomHeaders()));
        record.setDescription("全局自定义请求头（JSON 键值对）");
        record.setUpdater("");
        record.setUpdateTime(LocalDateTime.now());

        int rows = globalConfigMapper.updateByConfigKey(record);
        if (rows <= 0) {
            // 版本号为 0 表示首次保存（行可能不存在），尝试插入
            if (Long.valueOf(0L).equals(req.getVersionNo())) {
                try {
                    int inserted = globalConfigMapper.insertByConfigKey(record);
                    if (inserted > 0) {
                        log.info("[全局配置] 自定义请求头首次保存成功");
                        refreshRuntimeConfig();
                        return;
                    }
                } catch (DuplicateKeyException e) {
                    // 并发插入冲突，其他线程已先插入成功
                    log.warn("[全局配置] 并发首次保存冲突，按并发修改处理");
                }
            }
            throw new BizException("CONFIG_CONCURRENT_MODIFIED",
                    "全局配置已被其他请求修改，请刷新后重试");
        }
        log.info("[全局配置] 自定义请求头更新成功");
        refreshRuntimeConfig();
    }

    /**
     * 刷新运行时配置，失败时抛出异常
     */
    private void refreshRuntimeConfig() {
        if (!runtimeConfigRefreshService.reloadFromDb("admin-update-global-headers")) {
            throw new BizException("CONFIG_REFRESH_FAILED", "运行时配置刷新失败，请稍后重试");
        }
    }

    /**
     * 从数据库加载全局自定义请求头（供运行时刷新服务调用）
     */
    public Map<String, String> loadCustomHeadersFromDb() {
        GlobalConfigDO record = globalConfigMapper.selectByConfigKey(CUSTOM_HEADERS_KEY);
        if (record == null) {
            return Map.of();
        }
        return CustomHeaderUtils.parseHeadersJson(record.getConfigValue());
    }
}
