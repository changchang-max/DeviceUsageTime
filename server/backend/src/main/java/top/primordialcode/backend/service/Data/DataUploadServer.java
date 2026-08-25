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

        // 发布给指定用户（使用了秘钥的浏览器用户）
        try {
            handler.sendToUser(userEmail, data);
        } catch (Exception e) {
            // WebSocket推送失败不应该影响数据存储，只记录日志
            log.error("WebSocket推送失败: " + e.getMessage());
        }
    }
}
