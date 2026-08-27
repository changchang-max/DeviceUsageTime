package top.primordialcode.backend.service.Data;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.exception.UserNotFoundException;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.utils.JwtTokenUtil;
import top.primordialcode.backend.vo.data.RealtimeDataVO;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
public class DataQueryServer {

    @Autowired
    UserAuthMapper userAuthMapper;

    @Autowired
    RedisDataUploadServer redisDataUploadServer;

    @Autowired
    JwtTokenUtil jwtTokenUtil;

    /**
     * 获取用户当前实时数据
     * @param authorization Authorization请求头(可选，Bearer token)
     * @param key 用户秘钥(可选)
     * @return 实时数据VO
     */
    public RealtimeDataVO getRealtimeData(String authorization, String key) {
        // 解析用户身份(Token认证或秘钥认证)
        String userEmail = resolveUserEmail(authorization, key);

        // 校验用户是否存在
        if (!userAuthMapper.existsByEmail(userEmail)) {
            log.warn("用户不存在，邮箱：{}", userEmail);
            throw new UserNotFoundException("用户不存在");
        }

        try {
            // 读取Redis中的实时数据
            List<ApplicationDTO> applications =
                    redisDataUploadServer.getApplications(userEmail);
            StatisticsDTO statistics =
                    redisDataUploadServer.getStatistics(userEmail);
            RedisSaveOtherDataDTO otherData =
                    redisDataUploadServer.getOtherData(userEmail);

            Instant timestamp = (otherData != null)
                    ? otherData.getTimestamp()
                    : null;

            // 组装VO
            RealtimeDataVO vo = new RealtimeDataVO();
            vo.setUserId(userEmail);
            vo.setTimestamp(timestamp);
            vo.setDate(timestamp != null
                    ? timestamp.atZone(ZoneOffset.UTC).toLocalDate().toString()
                    : null);
            vo.setApplications(applications);
            vo.setStatistics(statistics);

            return vo;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("读取实时数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析用户身份，优先Token认证，其次秘钥认证
     * @param authorization Authorization请求头
     * @param key 用户秘钥
     * @return 用户邮箱
     */
    private String resolveUserEmail(String authorization, String key) {
        // Token认证
        if (authorization != null && !authorization.isBlank()) {
            try {
                return jwtTokenUtil.getSubject(authorization);
            } catch (RuntimeException e) {
                log.warn("Token解析失败: {}", e.getMessage());
                throw new SecurityException("Token无效");
            }
        }

        // 秘钥认证
        if (key != null && !key.isBlank()) {
            UserAuthEntity user = userAuthMapper.selectByKey(key);
            if (user == null) {
                log.warn("秘钥无效: {}", key);
                throw new SecurityException("秘钥无效");
            }
            return user.getUser_email();
        }

        // 未提供任何认证信息
        throw new SecurityException("未提供认证信息");
    }
}
