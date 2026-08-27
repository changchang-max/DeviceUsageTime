package top.primordialcode.backend.exception;

/**
 * 用户不存在异常
 * 用于数据查询等场景，表示通过Token或秘钥解析出的用户已不存在
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
