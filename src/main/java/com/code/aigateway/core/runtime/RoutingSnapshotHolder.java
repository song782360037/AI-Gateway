package com.code.aigateway.core.runtime;

import com.code.aigateway.core.router.RoutingConfigSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 路由配置快照持有器
 *
 * <p>使用 AtomicReference 实现本地快照的原子替换，
 * 保证路由热路径总能读取到一致的快照内容。</p>
 */
@Slf4j
@Component
public class RoutingSnapshotHolder {

    /** 当前生效的路由快照引用 */
    private final AtomicReference<RoutingConfigSnapshot> snapshotRef = new AtomicReference<>();

    /**
     * 是否存在脏标记。
     *
     * <p>当 Redis 预热失败或远程缓存状态不一致时置为 true，
     * 便于后台任务后续感知并补偿刷新。</p>
     */
    private volatile boolean dirty = false;

    /**
     * 原子替换当前快照。
     *
     * @param newSnapshot 新生成的路由配置快照
     */
    public void update(RoutingConfigSnapshot newSnapshot) {
        RoutingConfigSnapshot oldSnapshot = snapshotRef.getAndSet(newSnapshot);
        log.info("[快照更新] 版本: {} -> {}, 来源: {}, 别名数: {}",
                oldSnapshot != null ? oldSnapshot.getVersion() : "null",
                newSnapshot.getVersion(),
                newSnapshot.getSource(),
                newSnapshot.getCandidatesMapSize());

        // 快照更新成功后清除脏标记，表示本地内存状态已恢复一致。
        this.dirty = false;
    }

    /**
     * 获取当前快照。
     *
     * @return 当前快照；若尚未初始化则返回 null
     */
    public RoutingConfigSnapshot get() {
        return snapshotRef.get();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public boolean hasSnapshot() {
        return snapshotRef.get() != null;
    }
}
