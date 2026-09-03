package top.primordialcode.backend.service.Data;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.service.WebSocket.DeviceWebSocketHandler;
import top.primordialcode.backend.utils.JwtTokenUtil;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DataUploadServer {
    @Autowired
    JwtTokenUtil jwtTokenUtil;
    @Autowired
    RedisDataUploadServer redisDataUploadServer;
    @Autowired
    DeviceWebSocketHandler handler;
    @Autowired
    DataArchiveServer dataArchiveServer;

    public void receive(String Authorization, DataUploadMainDTO data){
        // 验证Authorization并获取用户邮箱
        String userEmail;
        try {
            userEmail = jwtTokenUtil.getSubject(Authorization);
        } catch (RuntimeException e) {
            throw new SecurityException("Token验证失败: " + e.getMessage());
        }

        // 验证数据完整性
        if (data.getTimestamp() == null) {
            throw new IllegalArgumentException("时间戳不能为空");
        }

        Instant timestamp = data.getTimestamp();
        List<ApplicationDTO> applications = data.getApplications();
        StatisticsDTO statistics = data.getStatistics();

        // 将其它数据封装进RedisSaveOtherDataDTO
        RedisSaveOtherDataDTO redisSaveOtherDataDTO = new RedisSaveOtherDataDTO();
        redisSaveOtherDataDTO.setUserEmail(userEmail);
        redisSaveOtherDataDTO.setTimestamp(timestamp);

        // 存入Redis
        try {
            redisDataUploadServer.updateOtherData(userEmail, redisSaveOtherDataDTO);
            
            // 只有当applications不为null且不为空时才更新
            if (applications != null && !applications.isEmpty()) {
                redisDataUploadServer.updateApplications(userEmail, applications);
            }
            
            // 只有当statistics不为null时才更新
            if (statistics != null) {
                redisDataUploadServer.updateStatistics(userEmail, statistics);
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败: " + e.getMessage(), e);
        }

        // 归档到MySQL冷数据(按用户+日期聚合)，失败只记日志，不影响实时数据与推送
        try {
            dataArchiveServer.archive(userEmail, timestamp, applications, statistics);
        } catch (Exception e) {
            log.error("历史数据归档失败: " + e.getMessage(), e);
        }

        // 推送给所有订阅该用户的查看者(协议7.4)
        // 推送Redis中的完整实时快照: 所有应用的累计数据 + 统计数据 + 最新时间戳
        try {
            List<ApplicationDTO> latestApplications =
                    redisDataUploadServer.getApplications(userEmail);
            StatisticsDTO latestStatistics =
                    redisDataUploadServer.getStatistics(userEmail);
            RedisSaveOtherDataDTO latestOtherData =
                    redisDataUploadServer.getOtherData(userEmail);

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
}
