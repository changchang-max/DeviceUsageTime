package top.primordialcode.backend.service.Redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RedisDataUploadServer {

    /** 实时热数据在Redis中的保留时长(天)，超期自动过期清理 */
    private static final long REDIS_TTL_DAYS = 3L;

    private final ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 构造器注入
    public RedisDataUploadServer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将Applications数据写入Redis(按归属日期分桶)
     *
     * Redis热数据必须按日期分桶，否则客户端补传/误传其他日期的数据会与
     * 当日实时数据混在同一份快照里，导致"实时(今日)"页面展示错误数据。
     *
     * @param email        用户邮箱
     * @param date         数据归属日期
     * @param applications Applications数据
     */
    public void updateApplications(String email, LocalDate date, List<ApplicationDTO> applications) {
        String key = applicationsKey(email, date);
        try {
            // 先序列化，避免在 Pipeline 回调内抛出受检异常
            Map<String, String> fieldMap = new LinkedHashMap<>();
            for (ApplicationDTO app : applications) {
                fieldMap.put(app.getName(), objectMapper.writeValueAsString(app));
            }

            // 使用 Pipeline 将 HSET 与 EXPIRE 打包为一批命令，避免写入成功但 TTL 设置失败导致内存泄漏（其实无法完全避免，Pipelined只能将两条命令打包在一起顺序发送，但无法保证其原子性，Redis也不会回滚）
            RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                byte[] rawKey = serializer.serialize(key);
                for (Map.Entry<String, String> entry : fieldMap.entrySet()) {
                    connection.hSet(rawKey,
                            serializer.serialize(entry.getKey()),
                            serializer.serialize(entry.getValue()));
                }
                connection.expire(rawKey, REDIS_TTL_DAYS * 86400);
                return null;
            });
            cleanLegacyKeys(email);
        } catch (JsonProcessingException e) {
            log.error("updateApplications JSON序列化失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("Applications数据序列化失败", e);
        } catch (Exception e) {
            log.error("updateApplications Redis写入失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("Applications数据写入Redis失败", e);
        }
    }

    /**
     * 将Statistics写入Redis(按归属日期分桶)
     *
     * @param email      用户邮箱
     * @param date       数据归属日期
     * @param statistics Statistics数据
     */
    public void updateStatistics(String email, LocalDate date, StatisticsDTO statistics) {
        String key = statisticsKey(email, date);
        try {
            String json = objectMapper.writeValueAsString(statistics);
            // SET key value EX ttl 原子命令，避免写入成功但 TTL 设置失败导致内存泄漏
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(REDIS_TTL_DAYS));
            cleanLegacyKeys(email);
        } catch (JsonProcessingException e) {
            log.error("updateStatistics JSON序列化失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("Statistics数据序列化失败", e);
        } catch (Exception e) {
            log.error("updateStatistics Redis写入失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("Statistics数据写入Redis失败", e);
        }
    }

    /**
     * 将除了Applications与Statistics的剩余数据组成一组，写入Redis(按归属日期分桶)
     *
     * @param email     用户邮箱
     * @param date      数据归属日期
     * @param otherData 剩余数据DTO
     */
    public void updateOtherData(String email, LocalDate date, RedisSaveOtherDataDTO otherData) {
        String key = otherDataKey(email, date);
        try {
            String json = objectMapper.writeValueAsString(otherData);
            // SET key value EX ttl 原子命令，避免写入成功但 TTL 设置失败导致内存泄漏
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(REDIS_TTL_DAYS));
            cleanLegacyKeys(email);
        } catch (JsonProcessingException e) {
            log.error("updateOtherData JSON序列化失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("OtherData数据序列化失败", e);
        } catch (Exception e) {
            log.error("updateOtherData Redis写入失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("OtherData数据写入Redis失败", e);
        }
    }

    /**
     * 从Redis读取指定日期的Applications数据
     *
     * @param email 用户邮箱
     * @param date  数据归属日期
     * @return 应用列表，若无数据则返回空列表
     */
    public List<ApplicationDTO> getApplications(String email, LocalDate date) {
        String key = applicationsKey(email, date);
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
            List<ApplicationDTO> applications = new ArrayList<>();
            for (Object value : entries.values()) {
                ApplicationDTO app = objectMapper.readValue((String) value, ApplicationDTO.class);
                applications.add(app);
            }
            return applications;
        } catch (JsonProcessingException e) {
            log.error("getApplications JSON反序列化失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("Applications数据反序列化失败", e);
        } catch (Exception e) {
            log.error("getApplications Redis读取失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("Applications数据读取Redis失败", e);
        }
    }

    /**
     * 从Redis读取指定日期的Statistics数据
     *
     * @param email 用户邮箱
     * @param date  数据归属日期
     * @return 统计数据，若无数据则返回null
     */
    public StatisticsDTO getStatistics(String email, LocalDate date) {
        String key = statisticsKey(email, date);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, StatisticsDTO.class);
        } catch (JsonProcessingException e) {
            log.error("getStatistics JSON反序列化失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("Statistics数据反序列化失败", e);
        } catch (Exception e) {
            log.error("getStatistics Redis读取失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("Statistics数据读取Redis失败", e);
        }
    }

    /**
     * 从Redis读取指定日期除Applications与Statistics之外的剩余数据
     *
     * @param email 用户邮箱
     * @param date  数据归属日期
     * @return 剩余数据DTO，若无数据则返回null
     */
    public RedisSaveOtherDataDTO getOtherData(String email, LocalDate date) {
        String key = otherDataKey(email, date);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, RedisSaveOtherDataDTO.class);
        } catch (JsonProcessingException e) {
            log.error("getOtherData JSON反序列化失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("OtherData数据反序列化失败", e);
        } catch (Exception e) {
            log.error("getOtherData Redis读取失败: email={}, date={}, error={}", email, date, e.getMessage(), e);
            throw new RuntimeException("OtherData数据读取Redis失败", e);
        }
    }

    /* ==================== Key与生命周期工具 ==================== */

    private String applicationsKey(String email, LocalDate date) {
        return "uploadDataDevice:applications:" + date + ":" + email;
    }

    private String statisticsKey(String email, LocalDate date) {
        return "uploadDataDevice:statistics:" + date + ":" + email;
    }

    private String otherDataKey(String email, LocalDate date) {
        return "uploadDataDevice:otherData:" + date + ":" + email;
    }

    /**
     * 清理升级前遗留的"无日期"旧key，避免旧快照长期残留Redis。
     * 失败只记日志，不阻断主流程。
     */
    private void cleanLegacyKeys(String email) {
        try {
            redisTemplate.delete(legacyApplicationsKey(email));
            redisTemplate.delete(legacyStatisticsKey(email));
            redisTemplate.delete(legacyOtherDataKey(email));
        } catch (Exception e) {
            log.warn("cleanLegacyKeys 清理旧key失败: email={}, error={}", email, e.getMessage(), e);
        }
    }

    private String legacyApplicationsKey(String email) {
        return "uploadDataDevice:applications:" + email;
    }

    private String legacyStatisticsKey(String email) {
        return "uploadDataDevice:statistics:" + email;
    }

    private String legacyOtherDataKey(String email) {
        return "uploadDataDevice:otherData:" + email;
    }
}
