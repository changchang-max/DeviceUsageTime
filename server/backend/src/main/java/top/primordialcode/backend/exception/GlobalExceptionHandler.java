package top.primordialcode.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.primordialcode.backend.common.Result;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * 统一把业务异常映射为响应体 { code, message, data }，Controller 不再手写 try/catch。
 * 同时遵循两条原则：
 * 1. 500/503 不把内部异常文案(如"redis 连接被拒绝")透传给前端；
 * 2. 任何异常都进日志，不再用 printStackTrace 写到 stdout。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理参数验证异常(@Valid 失败)
     *
     * @param ex 校验异常
     * @return 400 + 字段错误明细
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.put(fieldName, error.getDefaultMessage());
        });
        log.debug("请求参数校验失败: {}", errors);
        return Result.error(HttpStatus.BAD_REQUEST.value(), "数据格式错误", errors);
    }

    /**
     * 处理非法参数异常(业务校验失败)
     *
     * @param ex 异常
     * @return 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleIllegalArgumentException(IllegalArgumentException ex) {
        log.debug("请求参数非法: {}", ex.getMessage());
        return Result.error(HttpStatus.BAD_REQUEST.value(), ex.getMessage(), null);
    }

    /**
     * 处理安全异常(Token/秘钥无效、限流)
     *
     * @param ex 异常
     * @return 401
     */
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result handleSecurityException(SecurityException ex) {
        log.debug("认证失败: {}", ex.getMessage());
        return Result.error(HttpStatus.UNAUTHORIZED.value(), ex.getMessage(), null);
    }

    /**
     * 用户不存在
     *
     * @param ex 异常
     * @return 404
     */
    @ExceptionHandler(UserNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleUserNotFound(UserNotFoundException ex) {
        return Result.error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), null);
    }

    /**
     * 该日期无数据
     *
     * @param ex 异常
     * @return 404
     */
    @ExceptionHandler(DataNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleDataNotFound(DataNotFoundException ex) {
        return Result.error(HttpStatus.NOT_FOUND.value(), ex.getMessage(), null);
    }

    /**
     * 依赖服务不可用(Redis/邮件等)，客户端可重试
     *
     * @param ex 异常
     * @return 503
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result handleServiceUnavailable(ServiceUnavailableException ex) {
        log.error("依赖服务不可用: {}", ex.getMessage(), ex);
        return Result.error(HttpStatus.SERVICE_UNAVAILABLE.value(), "服务暂时不可用，请稍后重试", null);
    }

    /**
     * 通用运行时异常：记录完整堆栈，对外只回固定文案
     *
     * @param ex 异常
     * @return 500
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleRuntimeException(RuntimeException ex) {
        log.error("服务器内部异常", ex);
        return Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误", null);
    }

    /**
     * 请求路径不存在（如多余的尾斜杠 /api/auth/register/）时返回 404，
     * 避免被兜底处理器误报为 500。
     *
     * @param ex 资源未找到异常
     * @return 统一错误响应
     */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return Result.error(HttpStatus.NOT_FOUND.value(), "请求的资源不存在", null);
    }

    /**
     * 其他未捕获异常
     *
     * @param ex 异常
     * @return 500
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleException(Exception ex) {
        log.error("未捕获异常", ex);
        return Result.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误", null);
    }
}