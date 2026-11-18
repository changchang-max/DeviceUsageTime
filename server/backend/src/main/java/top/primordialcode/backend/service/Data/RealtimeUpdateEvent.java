package top.primordialcode.backend.service.Data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.List;

import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;

/**
 * 实时数据更新事件
 *
 * 上传接口在完成"校验 + Redis 写入"后立即发布本事件并返回 200，
 * 由 {@link RealtimeUpdateConsumer} 异步完成：
 * 1. MySQL 冷数据归档(失败可重试，且不影响实时页)；
 * 2. WebSocket 广播(同一用户的推送在时间窗内合帧)。
 *
 * 事件自带上传时的完整数据，消费端无需回查 Redis 才能归档，
 * 避免"消费时数据已被下一次上传覆盖"导致的历史数据丢失。
 */
@Slf4j
public class RealtimeUpdateEvent extends ApplicationEvent {

    /** 数据所属用户邮箱 */
    private final String userEmail;

    /** 上传时间戳 */
    private final Instant timestamp;

    /** 本次上传的应用列表(可为 null：表示本次未携带应用数据) */
    private final List<ApplicationDTO> applications;

    /** 本次上传的统计数据(可为 null) */
    private final StatisticsDTO statistics;

    /** 是否为测试数据(测试数据只存档、不推送) */
    private final boolean testData;

    /**
     * 构造事件
     *
     * @param source       事件源(发布者)
     * @param userEmail    用户邮箱
     * @param timestamp    上传时间戳
     * @param applications 应用列表
     * @param statistics   统计数据
     * @param testData     是否测试数据
     */
    public RealtimeUpdateEvent(Object source,
                               String userEmail,
                               Instant timestamp,
                               List<ApplicationDTO> applications,
                               StatisticsDTO statistics,
                               boolean testData) {
        super(source);
        this.userEmail = userEmail;
        this.timestamp = timestamp;
        this.applications = applications;
        this.statistics = statistics;
        this.testData = testData;
    }

    /**
     * @return 数据所属用户邮箱
     */
    public String getUserEmail() {
        return userEmail;
    }

    /**
     * @return 上传时间戳
     */
    public Instant getUploadTimestamp() {
        return timestamp;
    }

    /**
     * @return 应用列表(可能为 null)
     */
    public List<ApplicationDTO> getApplications() {
        return applications;
    }

    /**
     * @return 统计数据(可能为 null)
     */
    public StatisticsDTO getStatistics() {
        return statistics;
    }

    /**
     * @return 是否测试数据
     */
    public boolean isTestData() {
        return testData;
    }
}