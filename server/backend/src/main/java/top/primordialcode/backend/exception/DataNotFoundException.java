package top.primordialcode.backend.exception;

/**
 * 数据不存在异常
 * 用于历史数据查询等场景，表示指定日期下没有任何数据
 */
public class DataNotFoundException extends RuntimeException {

    public DataNotFoundException(String message) {
        super(message);
    }
}
