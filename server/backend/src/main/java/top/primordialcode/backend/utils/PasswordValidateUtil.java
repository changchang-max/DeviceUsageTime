package top.primordialcode.backend.utils;

/**
 * 密码格式校验工具类
 * 规则：至少8位，同时包含字母和数字
 */
public class PasswordValidateUtil {

    private static final int MIN_LENGTH = 8;

    // 至少8位，包含至少一个字母和至少一个数字
    private static final String PASSWORD_PATTERN =
            "^(?=.*[A-Za-z])(?=.*\\d).{" + MIN_LENGTH + ",}$";

    /**
     * 校验密码格式是否合法
     *
     * @param password 明文密码
     * @return true 表示合法，false 表示不符合要求
     */
    public static boolean isValid(String password) {
        if (password == null) {
            return false;
        }
        return password.matches(PASSWORD_PATTERN);
    }

    /**
     * 校验密码格式，不合法时直接抛出 IllegalArgumentException
     *
     * @param password 明文密码
     * @throws IllegalArgumentException 密码不符合格式要求时抛出
     */
    public static void validate(String password) {
        if (!isValid(password)) {
            throw new IllegalArgumentException("密码至少8位，且须同时包含字母和数字");
        }
    }
}
