package top.primordialcode.backend.service.Data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.service.UploadCredential.UploadCredentialServer;
import top.primordialcode.backend.service.WebSocket.DeviceWebSocketHandler;
import top.primordialcode.backend.utils.DataDateUtil;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DataUploadServer {
    @Autowired
    RedisDataUploadServer redisDataUploadServer;
    @Autowired
    DeviceWebSocketHandler handler;
    @Autowired
    DataArchiveServer dataArchiveServer;
    @Autowired
    UploadCredentialServer uploadCredentialServer;

    public void receive(DataUploadMainDTO data){
        // Resolve user identity from upload credential
        String userEmail = resolveUserIdentity(data);
        if (userEmail == null) {
            throw new IllegalArgumentException("上传凭证无效");
        }

        // Detect test data: if isTestData is true, skip WebSocket push
        boolean isTestData = Boolean.TRUE.equals(data.getIsTestData());

        // 验证数据完整性
        if (data.getTimestamp() == null) {
            throw new IllegalArgumentException("时间戳不能为空");
        }

        Instant timestamp = data.getTimestamp();
        List<ApplicationDTO> applications = data.getApplications();
        StatisticsDTO statistics = data.getStatistics();

        // 数据归属日期: 统一换算到 Asia/Shanghai 时区，
        // 避免使用UTC把东八区凌晨(00:00-07:59)上传的数据归到前一天
        LocalDate dataDate = DataDateUtil.toDataDate(timestamp);

        // 时间戳被降级解析(缺失时区或时区格式错误, 已按默认时区解析)时,
        // 将该情况标记为error, 并把上传用户与上传信息记录到日志
        if (data.isTimestampFallbackUsed()) {
            log.error("数据上传时间戳缺失或包含错误时区, 已按默认时区 {} 解析后正常入库。"
                            + "userId={}, 原始timestamp={}, applications=[{}], statistics={}",
                    DataDateUtil.DATA_ZONE.getId(), userEmail, data.getTimestampRaw(),
                    summarizeApplications(applications), summarizeStatistics(statistics));
        }

        // 将其它数据封装进RedisSaveOtherDataDTO
        RedisSaveOtherDataDTO redisSaveOtherDataDTO = new RedisSaveOtherDataDTO();
        redisSaveOtherDataDTO.setUserEmail(userEmail);
        redisSaveOtherDataDTO.setTimestamp(timestamp);

        // 存入Redis(按归属日期分桶: 补传/误传其他日期的数据不会污染今日实时快照)
        try {
            redisDataUploadServer.updateOtherData(userEmail, dataDate, redisSaveOtherDataDTO);
            
            // applications为null(本次上传未携带应用数据)时不更新;
            // 空数组表示当前无任何应用在运行, 同样需要调用以把快照中的应用标记为已关闭
            if (applications != null) {
                redisDataUploadServer.updateApplications(userEmail, dataDate, applications);
            }
            
            // 只有当statistics不为null时才更新
            if (statistics != null) {
                redisDataUploadServer.updateStatistics(userEmail, dataDate, statistics);
            }
        } catch (RuntimeException e) {
            log.error("Redis数据写入失败: email={}, date={}, error={}", userEmail, dataDate, e.getMessage(), e);
            throw e;
        }

        // 归档到MySQL冷数据(按用户+日期聚合)，失败只记日志，不影响实时数据与推送
        try {
            dataArchiveServer.archive(userEmail, timestamp, applications, statistics);
        } catch (Exception e) {
            log.error("历史数据归档失败: " + e.getMessage(), e);
        }

        // 如果是测试数据，不推送给前端
        if (isTestData) {
            log.debug("测试数据上传，不推送实时更新: userId={}, date={}", userEmail, dataDate);
            return;
        }

        // 仅当上传数据归属"今天"时才推送给实时查看者(协议7.4)。
        // 其他日期的上传(如客户端补传历史)只会归档进MySQL供日历查询，
        // 若也推送实时更新会把历史快照覆盖到查看者正在看的"今日实时页"。
        if (!dataDate.equals(DataDateUtil.today())) {
            log.debug("非今日数据上传, 不推送实时更新: userId={}, date={}", userEmail, dataDate);
            return;
        }

        // 推送给所有订阅该用户的查看者
        // 推送Redis中的完整实时快照: 所有应用的累计数据 + 统计数据 + 最新时间戳
        try {
            List<ApplicationDTO> latestApplications =
                    redisDataUploadServer.getApplications(userEmail, dataDate);
            StatisticsDTO latestStatistics =
                    redisDataUploadServer.getStatistics(userEmail, dataDate);
            RedisSaveOtherDataDTO latestOtherData =
                    redisDataUploadServer.getOtherData(userEmail, dataDate);

            Instant latestTimestamp =
                    (latestOtherData != null && latestOtherData.getTimestamp() != null)
                            ? latestOtherData.getTimestamp()
                            : timestamp;

            handler.pushRealtimeUpdate(
                    userEmail,
                    latestTimestamp,
                    // 无应用数据时推空数组，避免客户端解析null报错
                    latestApplications != null
                            ? latestApplications
                            : Collections.emptyList(),
                    latestStatistics
            );
        } catch (Exception e) {
            // WebSocket推送失败不应该影响数据存储，只记录日志
            log.error("WebSocket推送失败: " + e.getMessage(), e);
        }
    }

    /**
     从上传凭证中解析用户邮箱，若无法解析，则回退到 userEmail 字段。
     @param data 上传数据 DTO
     @return 解析后的用户邮箱；如果无法解析身份，则返回 null
     */
    private String resolveUserIdentity(DataUploadMainDTO data) {
        String credential = data.getUploadCredential();
        
        // Try upload credential first
        if (credential != null && !credential.isBlank()) {
            String email = uploadCredentialServer.resolveEmail(credential);
            if (email != null) {
                data.setUserEmail(email);
                return email;
            }
        }

        // Fallback to userEmail field in the request body (legacy support)
        if (data.getUserEmail() != null && !data.getUserEmail().isBlank()) {
            return data.getUserEmail();
        }

        return null;
    }

    /**
     * 汇总上传中的应用名称, 用于降级时间戳场景的日志记录
     */
    private String summarizeApplications(List<ApplicationDTO> applications) {
        if (applications == null || applications.isEmpty()) {
            return "无";
        }
        StringBuilder names = new StringBuilder();
        for (ApplicationDTO app : applications) {
            if (app != null && app.getName() != null && !app.getName().isBlank()) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(app.getName());
            }
        }
        return names.length() == 0 ? "无" : names.toString();
    }

    /**
     * 汇总统计数据, 用于降级时间戳场景的日志记录
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