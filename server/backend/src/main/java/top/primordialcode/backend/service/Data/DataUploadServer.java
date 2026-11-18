package top.primordialcode.backend.service.Data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.service.UploadCredential.UploadCredentialServer;
import top.primordialcode.backend.utils.DataDateUtil;
import top.primordialcode.backend.utils.MaskUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 数据上传服务(快收 + 异步落)
 *
 * 请求线程内只做三件事：
 * 1. 解析上传凭证(带缓存，命中时不查库)；
 * 2. 校验数据完整性(时间戳/时区/异常未来日期)；
 * 3. 一次 Lua 脚本把当日快照整体写进 Redis。
 *
 * 归档 MySQL 与 WebSocket 广播通过 {@link RealtimeUpdateEvent} 交给异步线程池，
 * 客户端响应时间不再随应用数量、订阅者数量线性增长。
 */
@Slf4j
@Service
public class DataUploadServer {

    @Autowired
    private RedisDataUploadServer redisDataUploadServer;

    @Autowired
    private UploadCredentialServer uploadCredentialServer;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 接收一次客户端上传
     *
     * @param data 上传数据(含凭证、时间戳、应用列表、统计数据)
     */
    public void receive(DataUploadMainDTO data) {
        // 解析用户身份(上传凭证优先，兼容旧版 userEmail 字段)
        String userEmail = resolveUserIdentity(data);
        if (userEmail == null) {
            throw new IllegalArgumentException("上传凭证无效");
        }

        // 校验数据完整性
        if (data.getTimestamp() == null) {
            throw new IllegalArgumentException("时间戳不能为空");
        }

        Instant timestamp = data.getTimestamp();
        List<ApplicationDTO> applications = data.getApplications();
        StatisticsDTO statistics = data.getStatistics();
        boolean isTestData = Boolean.TRUE.equals(data.getIsTestData());

        // 数据归属日期：统一换算到 Asia/Shanghai，避免 UTC 把凌晨数据归到前一天
        LocalDate dataDate = DataDateUtil.toDataDate(timestamp);

        // 时间戳被降级解析(缺失时区/时区格式错误)时记录错误日志，便于追查客户端问题
        if (data.isTimestampFallbackUsed()) {
            log.error("数据上传时间戳缺失或时区错误，已按默认时区 {} 解析。userId={}, 原始timestamp={}, 应用数={}, 统计={}",
                    DataDateUtil.DATA_ZONE.getId(), MaskUtil.maskEmail(userEmail), data.getTimestampRaw(),
                    applications != null ? applications.size() : 0, summarizeStatistics(statistics));
        }

        // 其它数据(用户邮箱 + 时间戳)
        RedisSaveOtherDataDTO otherData = new RedisSaveOtherDataDTO();
        otherData.setUserEmail(userEmail);
        otherData.setTimestamp(timestamp);

        // 一次性原子写入当日快照(applications / statistics / otherData + TTL)
        redisDataUploadServer.writeSnapshot(userEmail, dataDate, applications, statistics, otherData);

        // 归档与广播交给异步线程池；失败由消费者记日志，不影响本次上传结果
        eventPublisher.publishEvent(new RealtimeUpdateEvent(
                this, userEmail, timestamp, applications, statistics, isTestData));
    }

    /**
     * 从上传凭证中解析用户邮箱，若无法解析则回退到 userEmail 字段(兼容旧客户端)
     *
     * @param data 上传数据 DTO
     * @return 解析后的用户邮箱；无法解析身份时返回 null
     */
    private String resolveUserIdentity(DataUploadMainDTO data) {
        String credential = data.getUploadCredential();

        if (credential != null && !credential.isBlank()) {
            String email = uploadCredentialServer.resolveEmail(credential);
            if (email != null) {
                data.setUserEmail(email);
                return email;
            }
        }

        // 兼容旧版请求体直接携带 userEmail 的形式
        if (data.getUserEmail() != null && !data.getUserEmail().isBlank()) {
            return data.getUserEmail();
        }

        return null;
    }

    /**
     * 汇总统计数据，用于降级时间戳场景的日志记录
     *
     * @param statistics 统计数据
     * @return 一句话摘要
     */
    private String summarizeStatistics(StatisticsDTO statistics) {
        if (statistics == null) {
            return "无";
        }
        return "键盘=" + safeLong(statistics.getKeyboardCount()) + "次"
                + ", 鼠标点击=" + safeLong(statistics.getMouseClickCount()) + "次"
                + ", 鼠标移动=" + safeDouble(statistics.getMouseDistance()) + "m";
    }

    private long safeLong(Long value) {
        return value != null ? value : 0L;
    }

    private double safeDouble(Double value) {
        return value != null ? value : 0.0;
    }
}