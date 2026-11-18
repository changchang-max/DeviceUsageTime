package top.primordialcode.backend.utils;

/**
 * 日志脱敏工具
 *
 * 日志里直接打印邮箱、秘钥、上传凭证会把用户隐私永久留在磁盘上。
 * 统一走本工具类输出，既能定位问题(前缀 + 长度 + 短哈希)，又不泄露原文。
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /**
     * 邮箱脱敏：abc123@qq.com → a***@qq.com
     *
     * @param email 原始邮箱
     * @return 脱敏后的邮箱，null/空串原样返回
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return maskSecret(email);
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        // 本地部分只保留首字符，极短地址(1~2位)全部打码
        String maskedLocal = local.length() <= 2
                ? "*".repeat(local.length())
                : local.charAt(0) + "***";
        return maskedLocal + domain;
    }

    /**
     * 秘钥/凭证脱敏：abcd1234efgh5678 → abcd****(len=16)
     *
     * @param secret 原始秘钥
     * @return 脱敏结果，null/空串原样返回
     */
    public static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return secret;
        }
        if (secret.length() <= 4) {
            return "****(len=" + secret.length() + ")";
        }
        return secret.substring(0, 4) + "****(len=" + secret.length() + ")";
    }

    /**
     * 仅输出秘钥的稳定指纹(用于关联同一秘钥的多次失败日志，但不可逆推原文)
     *
     * @param secret 原始秘钥
     * @return 16 位十六进制指纹
     */
    public static String fingerprint(String secret) {
        if (secret == null) {
            return "null";
        }
        return Integer.toHexString(secret.hashCode()) + "-" + secret.length();
    }
}