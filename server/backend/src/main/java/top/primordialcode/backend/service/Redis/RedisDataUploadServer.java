package top.primordialcode.backend.service.Redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;

import java.util.List;

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
}
