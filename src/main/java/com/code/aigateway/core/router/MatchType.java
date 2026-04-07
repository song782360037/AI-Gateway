package com.code.aigateway.core.router;

/**
 * 模型别名匹配类型
 *
 * <p>定义路由规则中 alias_name 的匹配方式，影响路由查找策略。</p>
 *
 * <ul>
 *   <li>EXACT — 精确匹配（默认），alias_name 与请求模型名完全一致才命中</li>
 *   <li>GLOB — 通配符匹配，支持 {@code *}（任意字符序列）和 {@code ?}（单字符）</li>
 *   <li>REGEX — 正则匹配，alias_name 为 Java 正则表达式</li>
 * </ul>
 */
public enum MatchType {

    /** 精确匹配，HashMap O(1) 查找 */
    EXACT,

    /** 标准 glob 通配符：* 任意字符，? 单字符 */
    GLOB,

    /** Java 正则表达式 */
    REGEX
}
