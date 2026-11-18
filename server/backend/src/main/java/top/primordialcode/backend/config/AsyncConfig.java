package top.primordialcode.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 *
 * 上传链路的"MySQL 归档"与"WebSocket 广播"都放在这里执行，
 * HTTP 请求线程只负责校验 + Redis 写入，响应时间不再被数据库与推送拖累。
 *
 * 拒绝策略使用 CallerRunsPolicy：队列打满时退化为同步执行，
 * 保证数据不丢(宁可慢一点，也不能静默丢弃一次上传的归档)。
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /** 上传落库/广播专用线程池 Bean 名称 */
    public static final String UPLOAD_EXECUTOR = "uploadExecutor";

    /**
     * 上传链路异步线程池
     *
     * @return ThreadPoolTaskExecutor
     */
    @Bean(name = UPLOAD_EXECUTOR)
    public ThreadPoolTaskExecutor uploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(512);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("upload-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等待在途任务完成，避免停机时丢归档
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * @Async 方法未指定线程池时使用的默认执行器
     */
    @Override
    public Executor getAsyncExecutor() {
        return uploadExecutor();
    }

    /**
     * 异步方法中未捕获的异常默认会被静默吞掉，这里统一记录，避免"数据没落库但没有任何日志"
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("异步任务执行失败: method={}, error={}", method.getName(), throwable.getMessage(), throwable);
    }

    /**
     * 供需要"记录参数"的场景复用
     */
    public AsyncUncaughtExceptionHandler paramLoggingHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}