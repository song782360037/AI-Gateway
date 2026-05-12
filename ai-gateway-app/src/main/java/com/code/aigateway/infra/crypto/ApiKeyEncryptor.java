package com.code.aigateway.infra.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API Key 加解密组件。
 * <p>
 * 使用 AES-256-GCM 对提供商 API Key 做加密存储，
 * 避免明文密钥直接落库。
 * </p>
 */
@Slf4j
@Component
public class ApiKeyEncryptor {

    /** AES 算法名称。 */
    private static final String AES_ALGORITHM = "AES";

    /** GCM 加解密算法名称。 */
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";

    /** GCM 推荐使用 12 字节 IV。 */
    private static final int IV_LENGTH = 12;

    /** GCM 认证标签长度，单位为 bit。 */
    private static final int TAG_LENGTH_BIT = 128;

    /** AES-256 对应 32 字节密钥，即 64 个十六进制字符。 */
    private static final int SECRET_KEY_HEX_LENGTH = 64;

    /** 短字符串统一脱敏展示。 */
    private static final String MASK_VALUE = "****";

    /** 安全随机数生成器，用于生成 GCM IV。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 真正用于 AES-GCM 的 32 字节密钥。 */
    private final SecretKeySpec secretKeySpec;

    public ApiKeyEncryptor(@Value("${gateway.security.api-key-secret:}") String apiKeySecret) {
        // 为了兼容当前仓库的零配置本地运行，这里允许密钥暂时为空。
        // 真正执行加解密时再校验并抛出清晰错误，避免仅使用 YAML fallback 时应用无法启动。
        if (apiKeySecret == null || apiKeySecret.isBlank()) {
            this.secretKeySpec = null;
            log.warn("[API Key 加密器] 未配置 gateway.security.api-key-secret，持久化配置的加解密能力暂不可用");
            return;
        }

        validateSecret(apiKeySecret);
        this.secretKeySpec = new SecretKeySpec(hexToBytes(apiKeySecret), AES_ALGORITHM);
        log.info("[API Key 加密器] 初始化完成，已加载 AES-256-GCM 密钥配置");
    }

    /**
     * 加密明文 API Key。
     * <p>
     * 返回结果中将 IV 与密文分离，分别对应数据库中的 api_key_iv 与 api_key_ciphertext 字段。
     * 其中 ciphertext 实际包含密文与 GCM auth tag 的拼接结果。
     * </p>
     *
     * @param plainText 明文 API Key
     * @return 加密结果
     */
    public EncryptResult encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) {
            throw new IllegalArgumentException("plainText must not be blank");
        }
        ensureSecretConfigured();

        try {
            // 为每次加密生成独立随机 IV，避免重复 IV 带来的安全风险。
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);

            // GCM 模式输出结果已包含认证标签，无需额外拼接 tag。
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String ciphertextBase64 = Base64.getEncoder().encodeToString(cipherBytes);

            log.debug("[API Key 加密器] API Key 加密成功，密文长度: {}，掩码值: {}",
                    ciphertextBase64.length(), mask(plainText));
            return new EncryptResult(ivBase64, ciphertextBase64);
        } catch (Exception ex) {
            log.error("[API Key 加密器] API Key 加密失败", ex);
            throw new IllegalStateException("failed to encrypt api key", ex);
        }
    }

    /**
     * 解密 API Key。
     *
     * @param ivBase64 IV 的 Base64 编码值
     * @param ciphertextBase64 密文+认证标签的 Base64 编码值
     * @return 解密后的明文
     */
    public String decrypt(String ivBase64, String ciphertextBase64) {
        if (ivBase64 == null || ivBase64.isBlank()) {
            throw new IllegalArgumentException("ivBase64 must not be blank");
        }
        if (ciphertextBase64 == null || ciphertextBase64.isBlank()) {
            throw new IllegalArgumentException("ciphertextBase64 must not be blank");
        }
        ensureSecretConfigured();

        try {
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            if (iv.length != IV_LENGTH) {
                throw new IllegalArgumentException("iv length must be 12 bytes after Base64 decode");
            }

            byte[] cipherBytes = Base64.getDecoder().decode(ciphertextBase64);
            Cipher cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, gcmParameterSpec);

            byte[] plainBytes = cipher.doFinal(cipherBytes);
            String plainText = new String(plainBytes, StandardCharsets.UTF_8);
            log.debug("[API Key 加密器] API Key 解密成功，掩码值: {}", mask(plainText));
            return plainText;
        } catch (IllegalArgumentException ex) {
            log.error("[API Key 加密器] API Key 解密参数非法", ex);
            throw ex;
        } catch (Exception ex) {
            log.error("[API Key 加密器] API Key 解密失败", ex);
            throw new IllegalStateException("failed to decrypt api key", ex);
        }
    }

    /**
     * 对明文做脱敏展示。
     * <p>
     * 长度不足 13 位时直接返回统一掩码，
     * 避免因原文过短导致敏感信息泄露。
     * </p>
     *
     * @param plainText 明文内容
     * @return 掩码后的字符串
     */
    public String mask(String plainText) {
        if (plainText == null || plainText.length() < 13) {
            return MASK_VALUE;
        }

        // 保留前 8 位和后 4 位，中间统一替换为掩码标记。
        String prefix = plainText.substring(0, 8);
        String suffix = plainText.substring(plainText.length() - 4);
        return prefix + MASK_VALUE + suffix;
    }

    private void ensureSecretConfigured() {
        if (secretKeySpec == null) {
            throw new IllegalStateException("gateway.security.api-key-secret is not configured");
        }
    }

    private void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("gateway.security.api-key-secret must not be blank");
        }
        if (secret.length() != SECRET_KEY_HEX_LENGTH) {
            throw new IllegalArgumentException("gateway.security.api-key-secret must be 64 hex characters for AES-256");
        }
        if (!secret.matches("^[0-9a-fA-F]+$")) {
            throw new IllegalArgumentException("gateway.security.api-key-secret must contain only hex characters");
        }
    }

    private byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < hex.length(); index += 2) {
            int high = Character.digit(hex.charAt(index), 16);
            int low = Character.digit(hex.charAt(index + 1), 16);
            bytes[index / 2] = (byte) ((high << 4) + low);
        }
        return bytes;
    }

    /**
     * API Key 加密结果。
     *
     * @param iv IV 的 Base64 编码值
     * @param ciphertext 密文+认证标签的 Base64 编码值
     */
    public record EncryptResult(String iv, String ciphertext) {
    }
}
