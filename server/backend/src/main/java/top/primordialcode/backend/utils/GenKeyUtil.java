package top.primordialcode.backend.utils;

import java.security.SecureRandom;

public class GenKeyUtil {

    // 可使用的字符集合
    private static final String CHAR_POOL =
            "abcdefghijklmnopqrstuvwxyz" +
                    "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                    "0123456789" +
                    "!@#$%^&*";

    private static final SecureRandom RANDOM = new SecureRandom();


    /**
     * 生成随机Key
     *
     * @param length 长度（最大32）
     */
    public static String generateKey(int length) {

        if (length <= 0 || length > 32) {
            throw new IllegalArgumentException("长度必须在1~32之间");
        }

        String uuid = java.util.UUID.randomUUID()
                .toString()
                .replace("-", "");


        StringBuilder key = new StringBuilder();

        // 先加入UUID保证唯一性
        key.append(uuid);


        // 再加入随机字符增强随机性
        while (key.length() < length) {
            int index = RANDOM.nextInt(CHAR_POOL.length());
            key.append(CHAR_POOL.charAt(index));
        }


        // 截取指定长度
        return key.substring(0, length);
    }
}