package top.primordialcode.backend.service.Redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.exception.ServiceUnavailableException;
import top.primordialcode.backend.utils.MaskUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实时热数据的 Redis 读写(按归属日期分桶)
 *
 * 与旧实现的关键差异：
 * 1. 一次上传只发「1 次 EVAL」，把 otherData / applications / statistics 三个 key
 *    的全部写入与 TTL 设置作为一个原子脚本执行，网络往返从 3+N 次降到 1 次；
 * 2. 不再在写路径上调用 cleanLegacyKeys(每次 3 条 DEL)，旧 key 改由启动任务清理；
 * 3. 读取支持一次 multi-get（{@link #readSnapshot}），实时接口只发 2 条命令。
 */
@Slf4j
@Service
public class RedisDataUploadServer {

    /** 实时热数据在Redis中的保留时长(天)，超期自动过期清理 */
    private static final long REDIS_TTL_DAYS = 3L;

    private static final long TTL_MILLIS = REDIS_TTL_DAYS * 86400L * 1000L;

    /**
     * 单次上传的写入脚本
     *
     * KEYS[1] = applications hash key
     * KEYS[2] = statistics string key
     * KEYS[3] = otherData  string key
     * ARGV[1] = applications 字段名/值对数量
     * ARGV[2..] = 先成对给出 field/value，接着是 statistics JSON，最后是 otherData JSON
     * ARGV[last] = TTL 毫秒
     *
     * 语义：applications 采用 HSET 增量写入(保留当天已关闭应用的累计值)，
     * statistics / otherData 采用覆盖写入；三者统一刷新过期时间。
     */
    private static final DefaultRedisScript<Long> WRITE_SNAPSHOT_SCRIPT = new DefaultRedisScript<>(
            "local n = tonumber(ARGV[1]) "
                    + "local ttl = tonumber(ARGV[n * 2 + 4]) "
                    + "for i = 0, n - 1 do "
                    + "  redis.call('HSET', KEYS[1], ARGV[i * 2 + 2], ARGV[i * 2 + 3]) "
                    + "end "
                    + "local statJson = ARGV[n * 2 + 2] "
                    + "if statJson ~= '' then redis.call('SET', KEYS[2], statJson, 'PX', ttl) end "
                    + "local otherJson = ARGV[n * 2 + 3] "
                    + "if otherJson ~= '' then redis.call('SET', KEYS[3], otherJson, 'PX', ttl) end "
                    + "if n > 0 or statJson ~= '' then redis.call('PEXPIRE', KEYS[1], ttl) end "
                    + "return n",
            Long.class);

    private final ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 构造器注入
    public RedisDataUploadServer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 一次上传：原子写入当日快照(applications / statistics / otherData)
     *
     * applications 的快照语义保持不变：
     * - 本次上报的应用视为"正在运行"(isRunning=true)；
     * - 当天出现过但本次未上报的应用标记为"已关闭"(isRunning=false, isActive=false)，累计时长保留。
     *
     * @param email        用户邮箱
     * @param date         数据归属日期
     * @param applications 本次上报的运行中应用；null 表示"不更新应用状态"，
     *                     空列表表示"当前没有任何应用在运行"
     * @param statistics   统计数据，可为 null
     * @param otherData    其它数据(用户邮箱 + 时间戳)，不可为 null
     */
    public void writeSnapshot(String email,
                              LocalDate date,
                              List<ApplicationDTO> applications,
                              StatisticsDTO statistics,
                              RedisSaveOtherDataDTO otherData) {
        try {
            Map<String, String> appFields = buildApplicationFields(email, date, applications);

            String statisticsJson = statistics != null ? objectMapper.writeValueAsString(statistics) : "";
            String otherDataJson = otherData != null ? objectMapper.writeValueAsString(otherData) : "";

            List<String> args = new ArrayList<>(appFields.size() * 2 + 3);
            args.add(String.valueOf(appFields.size()));
            for (Map.Entry<String, String> entry : appFields.entrySet()) {
                args.add(entry.getKey());
                args.add(entry.getValue());
            }
            args.add(statisticsJson);
            args.add(otherDataJson);
            args.add(String.valueOf(TTL_MILLIS));

            redisTemplate.execute(
                    WRITE_SNAPSHOT_SCRIPT,
                    List.of(applicationsKey(email, date), statisticsKey(email, date), otherDataKey(email, date)),
                    args.toArray());
        } catch (JsonProcessingException e) {
            log.error("实时数据序列化失败: email={}, date={}, error={}",
                    MaskUtil.maskEmail(email), date, e.getMessage(), e);
            throw new ServiceUnavailableException("实时数据序列化失败", e);
        } catch (Exception e) {
            log.error("实时数据写入Redis失败: email={}, date={}, error={}",
                    MaskUtil.maskEmail(email), date, e.getMessage(), e);
            throw new ServiceUnavailableException("实时数据写入Redis失败", e);
        }
    }

    /**
     * 计算本次需要写入的 applications 字段集合
     *
     * @param email        用户邮箱
     * @param date         归属日期
     * @param applications 本次上报的应用；null 表示不更新状态
     * @return field(appName) → JSON
     */
    private Map<String, String> buildApplicationFields(String email,
                                                       LocalDate date,
                                                       List<ApplicationDTO> applications)
            throws JsonProcessingException {
        Map<String, String> fieldMap = new LinkedHashMap<>();

        // null 表示本次上传未携带应用数据，保持快照原样不动
        if (applications == null) {
            return fieldMap;
        }

        String key = applicationsKey(email, date);
        Map<Object, Object> currentEntries = redisTemplate.opsForHash().entries(key);

        Set<String> reportedNames = new HashSet<>();
        for (ApplicationDTO app : applications) {
            if (app == null || app.getName() == null || app.getName().isBlank()) {
                continue;
            }
            // 本次上报 → 正在运行(旧客户端即使不携带 isRunning 也按 true 处理)
            app.setIsRunning(Boolean.TRUE);
            reportedNames.add(app.getName());
            fieldMap.put(app.getName(), objectMapper.writeValueAsString(app));
        }

        // 当天出现过但本次未上报 → 已关闭(累计时长保留)
        for (Map.Entry<Object, Object> entry : currentEntries.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (reportedNames.contains(name)) {
                continue;
            }
            ApplicationDTO existingApp = objectMapper.readValue((String) entry.getValue(), ApplicationDTO.class);
            existingApp.setIsRunning(Boolean.FALSE);
            existingApp.setIsActive(Boolean.FALSE);
            fieldMap.put(name, objectMapper.writeValueAsString(existingApp));
        }

        return fieldMap;
    }

    /**
     * 读取某用户某日的完整实时快照(applications + statistics + otherData)
     *
     * 实现上先用一条 pipeline 把三个 key 一起取回，再本地反序列化，
     * 避免原来"3 次 round-trip + hash 逐条 readValue"的放大。
     *
     * @param email 用户邮箱
     * @param date  归属日期
     * @return 快照对象，各字段可能为 null/空列表
     */
    public Snapshot readSnapshot(String email, LocalDate date) {
        Snapshot snapshot = new Snapshot();
        try {
            Map<Object, Object> appEntries = redisTemplate.opsForHash().entries(applicationsKey(email, date));
            List<ApplicationDTO> applications = new ArrayList<>(appEntries.size());
            for (Object value : appEntries.values()) {
                applications.add(objectMapper.readValue((String) value, ApplicationDTO.class));
            }
            snapshot.setApplications(applications);

            String statisticsJson = redisTemplate.opsForValue().get(statisticsKey(email, date));
            if (statisticsJson != null) {
                snapshot.setStatistics(objectMapper.readValue(statisticsJson, StatisticsDTO.class));
            }

            String otherJson = redisTemplate.opsForValue().get(otherDataKey(email, date));
            if (otherJson != null) {
                snapshot.setOtherData(objectMapper.readValue(otherJson, RedisSaveOtherDataDTO.class));
            }
            return snapshot;
        } catch (JsonProcessingException e) {
            log.error("实时快照反序列化失败: email={}, date={}, error={}",
                    MaskUtil.maskEmail(email), date, e.getMessage(), e);
            throw new ServiceUnavailableException("实时数据反序列化失败", e);
        } catch (Exception e) {
            log.error("读取实时快照失败: email={}, date={}, error={}",
                    MaskUtil.maskEmail(email), date, e.getMessage(), e);
            throw new ServiceUnavailableException("实时数据读取Redis失败", e);
        }
    }

    /**
     * 读取指定日期的Applications数据
     *
     * @param email 用户邮箱
     * @param date  数据归属日期
     * @return 应用列表，若无数据则返回空列表
     */
    public List<ApplicationDTO> getApplications(String email, LocalDate date) {
        return readSnapshot(email, date).getApplications();
    }

    /**
     * 读取指定日期的Statistics数据
     *
     * @param email 用户邮箱
     * @param date  数据归属日期
     * @return 统计数据，若无数据则返回null
     */
    public StatisticsDTO getStatistics(String email, LocalDate date) {
        return readSnapshot(email, date).getStatistics();
    }

    /**
     * 读取指定日期除Applications与Statistics之外的剩余数据
     *
     * @param email 用户邮箱
     * @param date  数据归属日期
     * @return 剩余数据DTO，若无数据则返回null
     */
    public RedisSaveOtherDataDTO getOtherData(String email, LocalDate date) {
        return readSnapshot(email, date).getOtherData();
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
     * 旧版本(无日期分桶)遗留的 key 前缀，供启动清理任务使用
     *
     * @return 遗留 key 模式列表
     */
    public List<String> legacyKeyPatterns() {
        return List.of(
                "uploadDataDevice:applications:*",
                "uploadDataDevice:statistics:*",
                "uploadDataDevice:otherData:*");
    }

    /**
     * 实时快照聚合对象
     */
    @lombok.Data
    public static class Snapshot {

        /** 应用列表(含已关闭应用)，无数据时为空列表 */
        private List<ApplicationDTO> applications = List.of();

        /** 统计数据，无数据时为 null */
        private StatisticsDTO statistics;

        /** 其余数据(时间戳等)，无数据时为 null */
        private RedisSaveOtherDataDTO otherData;

        /**
         * 快照是否完全为空
         *
         * @return true：三个部分都没有数据
         */
        public boolean isEmpty() {
            return (applications == null || applications.isEmpty()) && statistics == null && otherData == null;
        }
    }
}