package com.code.aigateway.admin.mapper;

import com.code.aigateway.admin.model.dataobject.AutoRouteCandidateDO;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Auto 智能路由候选模型 Mapper
 */
@Mapper
public interface AutoRouteCandidateMapper {

    @Results(id = "autoRouteCandidateResultMap", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "configId", column = "config_id"),
            @Result(property = "providerCode", column = "provider_code"),
            @Result(property = "targetModel", column = "target_model"),
            @Result(property = "priority", column = "priority"),
            @Result(property = "weight", column = "weight"),
            @Result(property = "supportsVision", column = "supports_vision"),
            @Result(property = "supportsTools", column = "supports_tools"),
            @Result(property = "supportsToolChoiceRequired", column = "supports_tool_choice_required"),
            @Result(property = "supportsReasoning", column = "supports_reasoning"),
            @Result(property = "supportsJson", column = "supports_json"),
            @Result(property = "supportsStream", column = "supports_stream"),
            @Result(property = "maxInputTokens", column = "max_input_tokens"),
            @Result(property = "maxOutputTokens", column = "max_output_tokens"),
            @Result(property = "qualityScore", column = "quality_score"),
            @Result(property = "latencyScore", column = "latency_score"),
            @Result(property = "costScore", column = "cost_score"),
            @Result(property = "toolScore", column = "tool_score"),
            @Result(property = "visionScore", column = "vision_score"),
            @Result(property = "reasoningScore", column = "reasoning_score"),
            @Result(property = "reliabilityScore", column = "reliability_score"),
            @Result(property = "scoreBias", column = "score_bias"),
            @Result(property = "enabled", column = "enabled"),
            @Result(property = "description", column = "description"),
            @Result(property = "versionNo", column = "version_no"),
            @Result(property = "creator", column = "creator"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updater", column = "updater"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "deleted", column = "deleted")
    })
    @Select("""
            SELECT id, config_id, provider_code, target_model, priority, weight,
                   supports_vision, supports_tools, supports_tool_choice_required, supports_reasoning,
                   supports_json, supports_stream, max_input_tokens, max_output_tokens,
                   quality_score, latency_score, cost_score, tool_score, vision_score, reasoning_score,
                   reliability_score, score_bias, enabled, description, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM auto_route_candidate
            WHERE id = #{id}
              AND deleted = 0
            """)
    AutoRouteCandidateDO selectById(@Param("id") Long id);

    @Insert("""
            INSERT INTO auto_route_candidate (
                config_id, provider_code, target_model, priority, weight,
                supports_vision, supports_tools, supports_tool_choice_required, supports_reasoning,
                supports_json, supports_stream, max_input_tokens, max_output_tokens,
                quality_score, latency_score, cost_score, tool_score, vision_score, reasoning_score,
                reliability_score, score_bias, enabled, description, version_no,
                creator, create_time, updater, update_time, deleted
            ) VALUES (
                #{configId}, #{providerCode}, #{targetModel}, #{priority}, #{weight},
                #{supportsVision}, #{supportsTools}, #{supportsToolChoiceRequired}, #{supportsReasoning},
                #{supportsJson}, #{supportsStream}, #{maxInputTokens}, #{maxOutputTokens},
                #{qualityScore}, #{latencyScore}, #{costScore}, #{toolScore}, #{visionScore}, #{reasoningScore},
                #{reliabilityScore}, #{scoreBias}, #{enabled}, #{description}, #{versionNo},
                #{creator}, #{createTime}, #{updater}, #{updateTime}, #{deleted}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AutoRouteCandidateDO record);

    @Update("""
            UPDATE auto_route_candidate
            SET provider_code = #{providerCode},
                target_model = #{targetModel},
                priority = #{priority},
                weight = #{weight},
                supports_vision = #{supportsVision},
                supports_tools = #{supportsTools},
                supports_tool_choice_required = #{supportsToolChoiceRequired},
                supports_reasoning = #{supportsReasoning},
                supports_json = #{supportsJson},
                supports_stream = #{supportsStream},
                max_input_tokens = #{maxInputTokens},
                max_output_tokens = #{maxOutputTokens},
                quality_score = #{qualityScore},
                latency_score = #{latencyScore},
                cost_score = #{costScore},
                tool_score = #{toolScore},
                vision_score = #{visionScore},
                reasoning_score = #{reasoningScore},
                reliability_score = #{reliabilityScore},
                score_bias = #{scoreBias},
                enabled = #{enabled},
                description = #{description},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateById(AutoRouteCandidateDO record);

    @Update("""
            UPDATE auto_route_candidate
            SET deleted = 1,
                provider_code = CONCAT(LEFT(provider_code, 64 - CHAR_LENGTH(CONCAT('_del_', id))), '_del_', id),
                target_model = CONCAT(LEFT(target_model, 128 - CHAR_LENGTH(CONCAT('_del_', id))), '_del_', id),
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int softDeleteById(AutoRouteCandidateDO record);

    /**
     * 按 configId 批量软删除候选模型。
     * <p>
     * 不使用乐观锁（version_no）条件：此操作仅在删除配置时于事务内调用，
     * 此时配置记录已持有乐观锁，并发更新候选的窗口极小且事务隔离可保证一致性。
     * </p>
     */
    @Update("""
            UPDATE auto_route_candidate
            SET deleted = 1,
                provider_code = CONCAT(LEFT(provider_code, 64 - CHAR_LENGTH(CONCAT('_del_', id))), '_del_', id),
                target_model = CONCAT(LEFT(target_model, 128 - CHAR_LENGTH(CONCAT('_del_', id))), '_del_', id),
                updater = #{updater},
                update_time = #{updateTime}
            WHERE config_id = #{configId}
              AND deleted = 0
            """)
    int softDeleteByConfigId(AutoRouteCandidateDO record);

    @Select("""
            SELECT id, config_id, provider_code, target_model, priority, weight,
                   supports_vision, supports_tools, supports_tool_choice_required, supports_reasoning,
                   supports_json, supports_stream, max_input_tokens, max_output_tokens,
                   quality_score, latency_score, cost_score, tool_score, vision_score, reasoning_score,
                   reliability_score, score_bias, enabled, description, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM auto_route_candidate
            WHERE config_id = #{configId}
              AND deleted = 0
            ORDER BY priority DESC, id ASC
            """)
    @ResultMap("autoRouteCandidateResultMap")
    List<AutoRouteCandidateDO> selectByConfigId(@Param("configId") Long configId);

    @Select("""
            SELECT id, config_id, provider_code, target_model, priority, weight,
                   supports_vision, supports_tools, supports_tool_choice_required, supports_reasoning,
                   supports_json, supports_stream, max_input_tokens, max_output_tokens,
                   quality_score, latency_score, cost_score, tool_score, vision_score, reasoning_score,
                   reliability_score, score_bias, enabled, description, version_no,
                   creator, create_time, updater, update_time, deleted
            FROM auto_route_candidate
            WHERE enabled = 1
              AND deleted = 0
            ORDER BY priority DESC, id ASC
            """)
    @ResultMap("autoRouteCandidateResultMap")
    List<AutoRouteCandidateDO> selectAllEnabled();

    @Select("""
            SELECT COUNT(1)
            FROM auto_route_candidate
            WHERE config_id = #{configId}
              AND provider_code = #{providerCode}
              AND target_model = #{targetModel}
              AND deleted = 0
            """)
    int existsCandidate(@Param("configId") Long configId,
                        @Param("providerCode") String providerCode,
                        @Param("targetModel") String targetModel);

    @Select("""
            SELECT COUNT(1)
            FROM auto_route_candidate
            WHERE provider_code = #{providerCode}
              AND enabled = 1
              AND deleted = 0
            """)
    int existsEnabledByProviderCode(@Param("providerCode") String providerCode);

    @Select("""
            SELECT COUNT(1)
            FROM auto_route_candidate
            WHERE config_id = #{configId}
              AND deleted = 0
            """)
    int countByConfigId(@Param("configId") Long configId);

    /**
     * 批量统计每个 configId 的候选数量，避免 N+1 查询
     */
    @Select("""
            <script>
            SELECT config_id AS configId, COUNT(1) AS cnt
            FROM auto_route_candidate
            WHERE deleted = 0
              AND config_id IN
              <foreach collection='configIds' item='id' open='(' separator=',' close=')'>
                #{id}
              </foreach>
            GROUP BY config_id
            </script>
            """)
    @Results({
            @Result(property = "configId", column = "configId"),
            @Result(property = "cnt", column = "cnt")
    })
    List<java.util.Map<String, Object>> countByConfigIds(@Param("configIds") List<Long> configIds);

    @Update("""
            UPDATE auto_route_candidate
            SET enabled = #{enabled},
                version_no = version_no + 1,
                updater = #{updater},
                update_time = #{updateTime}
            WHERE id = #{id}
              AND version_no = #{versionNo}
              AND deleted = 0
            """)
    int updateEnabled(AutoRouteCandidateDO record);
}
