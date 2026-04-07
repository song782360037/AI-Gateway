package com.code.aigateway.core.router;

/**
 * Glob 通配符转正则工具
 *
 * <p>将标准 glob 模式转换为 Java 正则表达式，
 * 供 {@link com.code.aigateway.admin.service.RuntimeConfigRefreshService} 快照构建和
 * {@link com.code.aigateway.admin.service.ModelRedirectConfigServiceImpl} 语法校验共用。</p>
 *
 * <ul>
 *   <li>{@code *} → {@code .*}（任意字符序列）</li>
 *   <li>{@code ?} → {@code .}（任意单个字符）</li>
 *   <li>其他正则元字符自动转义</li>
 * </ul>
 */
public final class GlobPatternUtil {

    private GlobPatternUtil() {
    }

    /**
     * 将 glob 模式转换为完全匹配的 Java 正则表达式（自带 ^ 和 $ 锚点）。
     */
    public static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder(glob.length() * 2);
        sb.append("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                // 正则元字符需要转义
                case '.', '^', '$', '+', '[', ']', '(', ')', '{', '}', '|', '\\' -> {
                    sb.append('\\');
                    sb.append(c);
                }
                default -> sb.append(c);
            }
        }
        sb.append("$");
        return sb.toString();
    }
}
