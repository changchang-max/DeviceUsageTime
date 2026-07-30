package top.primordialcode.backend.service.Data;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.service.WebSocket.DeviceWebSocketHandler;
import top.primordialcode.backend.utils.JwtTokenUtil;


import java.time.Instant;
import java.util.List;

@Service
public class DataUploadServer {
    @Autowired
    JwtTokenUtil jwtTokenUtil;
    @Autowired
    RedisDataUploadServer redisDataUploadServer;
    @Autowired
    DeviceWebSocketHandler handler;

    public Result receive(String Authorization, DataUploadMainDTO data){
        //获取data中的数据，分类存入Redis
        String userEmail = jwtTokenUtil.getSubject(Authorization);
        Instant timestamp = data.getTimestamp();
        // 将其它数据封装进RedisSaveOtherDataDTO
        RedisSaveOtherDataDTO redisSaveOtherDataDTO = new RedisSaveOtherDataDTO();
        redisSaveOtherDataDTO.setUserEmail(userEmail);
        redisSaveOtherDataDTO.setTimestamp(timestamp);

        List<ApplicationDTO> applications = data.getApplications();

        StatisticsDTO statistics = data.getStatistics();

        //存入Redis
        try {
            redisDataUploadServer.updateOtherData(userEmail,redisSaveOtherDataDTO);
            redisDataUploadServer.updateApplications(userEmail,applications);
            redisDataUploadServer.updateStatistics(userEmail,statistics);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败，可能是原始信息有误。详细异常："+e);
        }

        // 发布给指定用户（使用了秘钥的浏览器用户）
        try {
            handler.sendToUser(userEmail,data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return Result.success();
    }
}
