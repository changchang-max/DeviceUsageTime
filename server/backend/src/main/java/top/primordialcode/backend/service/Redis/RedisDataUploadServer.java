package top.primordialcode.backend.service.Redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RedisDataUploadServer {

    private final ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;
    // 构造器注入
    public RedisDataUploadServer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将Applications数据写入Redis
     * @param email 用户邮箱
     * @param applications Applications数据
     * @throws JsonProcessingException
     */
    public void updateApplications(String email, List<ApplicationDTO> applications)
            throws JsonProcessingException {
        String key = "uploadDataDevice:applications:" + email;
        for (ApplicationDTO app : applications){
            String json = objectMapper.writeValueAsString(app);

            //以Hash结构放入Redis
            redisTemplate.opsForHash().put(
                    key,
                    app.getName(),
                    json
            );
        }
    }

    /**
     * 将Statistics写入Redis
     * @param email 用户邮箱
     * @param statistics Statistics数据
     * @throws JsonProcessingException
     */
    public void updateStatistics(String email, StatisticsDTO statistics)
        throws JsonProcessingException{

        String key = "uploadDataDevice:statistics:" + email;
        String json = objectMapper.writeValueAsString(statistics);

        redisTemplate.opsForValue().set(key,json);
    }

    /**
     * 将除了Applications与Statistics的剩余数据组成一组，写入Redis
     * @param email 用户邮箱
     * @param otherData 剩余数据DTO
     * @throws JsonProcessingException
     */
    public void updateOtherData(String email, RedisSaveOtherDataDTO otherData)
        throws JsonProcessingException{

        String key = "uploadDataDevice:otherData:" + email;
        String json = objectMapper.writeValueAsString(otherData);

        redisTemplate.opsForValue().set(key,json);
    }

    /**
     * 从Redis读取Applications数据
     * @param email 用户邮箱
     * @return 应用列表，若无数据则返回空列表
     * @throws JsonProcessingException
     */
    public List<ApplicationDTO> getApplications(String email)
            throws JsonProcessingException {
        String key = "uploadDataDevice:applications:" + email;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);

        List<ApplicationDTO> applications = new ArrayList<>();
        for (Object value : entries.values()) {
            ApplicationDTO app = objectMapper.readValue(
                    (String) value,
                    ApplicationDTO.class
            );
            applications.add(app);
        }
        return applications;
    }

    /**
     * 从Redis读取Statistics数据
     * @param email 用户邮箱
     * @return 统计数据，若无数据则返回null
     * @throws JsonProcessingException
     */
    public StatisticsDTO getStatistics(String email)
            throws JsonProcessingException {
        String key = "uploadDataDevice:statistics:" + email;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return null;
        }
        return objectMapper.readValue(json, StatisticsDTO.class);
    }

    /**
     * 从Redis读取除Applications与Statistics之外的剩余数据
     * @param email 用户邮箱
     * @return 剩余数据DTO，若无数据则返回null
     * @throws JsonProcessingException
     */
    public RedisSaveOtherDataDTO getOtherData(String email)
            throws JsonProcessingException {
        String key = "uploadDataDevice:otherData:" + email;
        String json = redisTemplate.opsForValue().get(key);

        if (json == null) {
            return null;
        }
        return objectMapper.readValue(json, RedisSaveOtherDataDTO.class);
    }
}
