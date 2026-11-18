package top.primordialcode.backend.exception;

/**
 * 上传链路依赖不可用(Redis 读写失败等)
 *
 * 独立成异常类型，让全局处理器能稳定映射为 503(可重试)，
 * 而不是被兜底成 500(客户端会当成"数据格式/逻辑错误"而放弃重试)。
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}