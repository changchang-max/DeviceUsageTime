package top.primordialcode.backend.service.Data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.config.CacheConfig.CacheNames;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.entity.AppUsageRecordEntity;
import top.primordialcode.backend.entity.AppWindowTitleEntity;
import top.primordialcode.backend.entity.DailyStatisticsEntity;
import top.primordialcode.backend.entity.DataDateIndexEntity;
import top.primordialcode.backend.exception.DataNotFoundException;
import top.primordialcode.backend.exception.UserNotFoundException;
import top.primordialcode.backend.mapper.HistoryDataMapper;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.utils.DataDateUtil;
import top.primordialcode.backend.utils.JwtTokenUtil;
import top.primordialcode.backend.utils.MaskUtil;
import top.primordialcode.backend.utils.RateLimiterUtil;
import top.primordialcode.backend.utils.UserLookupCache;
import top.primordialcode.backend.vo.data.DataDatesVO;
import top.primordialcode.backend.vo.data.HistoryApplicationVO;
import top.primordialcode.backend.vo.data.HistoryDataVO;
import top.primordialcode.backend.vo.data.RealtimeDataVO;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据查询服务
 *
 * 性能要点：
 * 1. 身份解析(秘钥→邮箱)走 Caffeine 缓存，查看者每秒轮询不会再打 MySQL；
 * 2. 实时快照一次读 Redis 的 3 个 key(而不是逐个读 + 逐条反序列化)；
 * 3. 窗口标题默认不返回，改为按需懒加载接口，避免历史页响应体膨胀；
 * 4. 秘钥校验失败带限流，防止被暴力枚举。
 */
@Slf4j
@Service
public class DataQueryServer {

    @Autowired
    private UserLookupCache userLookupCache;

    @Autowired
    private RedisDataUploadServer redisDataUploadServer;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private HistoryDataMapper historyDataMapper;

    @Autowired
    private RateLimiterUtil rateLimiterUtil;

    /** 秘钥校验窗口内允许的最大失败次数 */
    @Value("${security.key-verify.max-failures:10}")
    private int keyVerifyMaxFailures;

    /** 秘钥校验限流窗口(秒) */
    @Value("${security.key-verify.window-seconds:60}")
    private long keyVerifyWindowSeconds;

    /**
     * 获取用户当前实时数据
     *
     * @param authorization Authorization请求头(可选，Bearer token)
     * @param key           用户秘钥(可选)
     * @param clientIp      客户端IP(用于限流，可为 null)
     * @return 实时数据VO
     */
    public RealtimeDataVO getRealtimeData(String authorization, String key, String clientIp) {
        String userEmail = resolveUserEmail(authorization, key, clientIp);

        if (!userLookupCache.existsByEmail(userEmail)) {
            log.warn("用户不存在，邮箱：{}", MaskUtil.maskEmail(userEmail));
            throw new UserNotFoundException("用户不存在");
        }

        // 实时数据即"今日"的热数据快照：以统一时区(Asia/Shanghai)当天分桶读取
        LocalDate today = DataDateUtil.today();
        RedisDataUploadServer.Snapshot snapshot = redisDataUploadServer.readSnapshot(userEmail, today);

        RedisSaveOtherDataDTO otherData = snapshot.getOtherData();
        Instant timestamp = otherData != null ? otherData.getTimestamp() : null;

        RealtimeDataVO vo = new RealtimeDataVO();
        vo.setUserId(userEmail);
        vo.setTimestamp(timestamp);
        vo.setDate(today.toString());
        vo.setApplications(toRealtimeApplications(snapshot.getApplications()));
        vo.setStatistics(snapshot.getStatistics());
        return vo;
    }

    /**
     * 获取指定日期的历史数据(MySQL冷数据)
     *
     * @param authorization Authorization请求头(可选，Bearer token)
     * @param key           用户秘钥(可选)
     * @param clientIp      客户端IP(用于限流，可为 null)
     * @param dateStr       日期(YYYY-MM-DD)
     * @param includeTitles 是否同时返回窗口标题(默认 false，由懒加载接口单独取)
     * @return 历史数据VO
     */
    public HistoryDataVO getHistoryData(String authorization, String key, String clientIp,
                                        String dateStr, boolean includeTitles) {
        String userEmail = resolveUserEmail(authorization, key, clientIp);

        if (!userLookupCache.existsByEmail(userEmail)) {
            log.warn("用户不存在，邮箱：{}", MaskUtil.maskEmail(userEmail));
            throw new UserNotFoundException("用户不存在");
        }

        LocalDate date = parseDate(dateStr);

        DailyStatisticsEntity statisticsEntity = historyDataMapper.selectDailyStatistics(userEmail, date);
        List<AppUsageRecordEntity> records = historyDataMapper.selectAppRecords(userEmail, date);

        if ((records == null || records.isEmpty()) && statisticsEntity == null) {
            throw new DataNotFoundException("该日期无数据");
        }

        // 标题按需批量查询(默认不查，避免历史页响应体过大)
        Map<Long, List<String>> titlesByRecordId = new HashMap<>();
        if (includeTitles && records != null && !records.isEmpty()) {
            List<Long> recordIds = new ArrayList<>(records.size());
            for (AppUsageRecordEntity record : records) {
                recordIds.add(record.getId());
            }
            List<AppWindowTitleEntity> windowTitles = historyDataMapper.selectTitlesByRecordIds(recordIds);
            for (AppWindowTitleEntity title : windowTitles) {
                titlesByRecordId
                        .computeIfAbsent(title.getRecord_id(), k -> new ArrayList<>())
                        .add(title.getWindow_title());
            }
        }

        HistoryDataVO vo = new HistoryDataVO();
        vo.setDate(date.toString());

        List<HistoryApplicationVO> applicationVOs = new ArrayList<>();
        if (records != null) {
            for (AppUsageRecordEntity record : records) {
                HistoryApplicationVO appVO = new HistoryApplicationVO();
                appVO.setName(record.getApp_name());
                appVO.setTotalDuration(record.getTotal_duration() != null ? record.getTotal_duration() : 0L);
                appVO.setSessions(record.getSessions() != null ? record.getSessions() : 0L);
                List<String> titles = titlesByRecordId.get(record.getId());
                appVO.setWindowTitles(titles != null ? titles : new ArrayList<>());
                applicationVOs.add(appVO);
            }
        }
        vo.setApplications(applicationVOs);
        vo.setStatistics(toStatisticsVO(statisticsEntity));
        return vo;
    }

    /**
     * 懒加载某个应用在某天的窗口标题(历史页展开时调用)
     *
     * @param authorization Authorization请求头(可选，Bearer token)
     * @param key           用户秘钥(可选)
     * @param clientIp      客户端IP(用于限流，可为 null)
     * @param dateStr       日期(YYYY-MM-DD)
     * @param appName       应用名称
     * @return 窗口标题列表(可能为空)
     */
    public List<String> getWindowTitles(String authorization, String key, String clientIp,
                                        String dateStr, String appName) {
        String userEmail = resolveUserEmail(authorization, key, clientIp);
        LocalDate date = parseDate(dateStr);

        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("应用名称不能为空");
        }

        AppUsageRecordEntity record = historyDataMapper.selectAppRecord(userEmail, date, appName);
        if (record == null) {
            return new ArrayList<>();
        }

        List<AppWindowTitleEntity> titles =
                historyDataMapper.selectTitlesByRecordIds(List.of(record.getId()));
        List<String> result = new ArrayList<>(titles.size());
        for (AppWindowTitleEntity title : titles) {
            result.add(title.getWindow_title());
        }
        return result;
    }

    /**
     * 获取某个月份内有数据记录的日期列表(基于 data_date_index 日期索引)
     *
     * 结果按用户+年月缓存 60 秒：日历每次翻月才会变化，不必每次都查库。
     *
     * @param authorization Authorization请求头(可选，Bearer token)
     * @param key           用户秘钥(可选)
     * @param clientIp      客户端IP(用于限流，可为 null)
     * @param yearMonthStr  年月(YYYY-MM)
     * @return 有数据日期列表VO
     */
    public DataDatesVO getDataDates(String authorization, String key, String clientIp, String yearMonthStr) {
        String userEmail = resolveUserEmail(authorization, key, clientIp);

        if (!userLookupCache.existsByEmail(userEmail)) {
            log.warn("用户不存在，邮箱：{}", MaskUtil.maskEmail(userEmail));
            throw new UserNotFoundException("用户不存在");
        }

        YearMonth yearMonth = parseYearMonth(yearMonthStr);

        DataDatesVO vo = new DataDatesVO();
        vo.setYearMonth(yearMonth.toString());
        vo.setDates(cachedDates(userEmail, yearMonth));
        return vo;
    }

    /**
     * 带缓存的日期索引查询
     *
     * @param userEmail 用户邮箱
     * @param yearMonth 年月
     * @return 有数据的日期(YYYY-MM-DD，升序)
     */
    @Cacheable(cacheNames = CacheNames.DATA_DATES, key = "#userEmail + ':' + #yearMonth")
    public List<String> cachedDates(String userEmail, YearMonth yearMonth) {
        List<DataDateIndexEntity> dateIndexes = historyDataMapper.selectDateIndexesInRange(
                userEmail, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        List<String> dates = new ArrayList<>();
        if (dateIndexes != null) {
            for (DataDateIndexEntity dateIndex : dateIndexes) {
                dates.add(dateIndex.getStat_date().toString());
            }
        }
        return dates;
    }

    /**
     * 实时应用列表转换：过滤空名称并按时长降序
     *
     * @param applications Redis 中的应用列表
     * @return 排序后的列表
     */
    private List<ApplicationDTO> toRealtimeApplications(List<ApplicationDTO> applications) {
        if (applications == null || applications.isEmpty()) {
            return new ArrayList<>();
        }
        List<ApplicationDTO> result = new ArrayList<>(applications);
        result.removeIf(app -> app == null || app.getName() == null || app.getName().isBlank());
        result.sort((a, b) -> Long.compare(safeDuration(b), safeDuration(a)));
        return result;
    }

    private long safeDuration(ApplicationDTO app) {
        return app.getDuration() != null ? app.getDuration() : 0L;
    }

    /**
     * 将每日统计数据实体转换为VO(缺省字段补0)
     *
     * @param entity 每日统计实体(可为 null)
     * @return 统计VO
     */
    private StatisticsDTO toStatisticsVO(DailyStatisticsEntity entity) {
        StatisticsDTO statistics = new StatisticsDTO();
        statistics.setKeyboardCount(
                entity != null && entity.getKeyboard_count() != null ? entity.getKeyboard_count() : 0L);
        statistics.setMouseClickCount(
                entity != null && entity.getMouse_click_count() != null ? entity.getMouse_click_count() : 0L);
        statistics.setMouseDistance(
                entity != null && entity.getMouse_distance() != null
                        ? entity.getMouse_distance().doubleValue() : 0.0);
        statistics.setTotalDuration(
                entity != null && entity.getTotal_duration() != null ? entity.getTotal_duration() : 0L);
        return statistics;
    }

    /**
     * 校验日期参数，必须是合法的 YYYY-MM-DD
     *
     * @param dateStr 日期字符串
     * @return 解析后的日期
     */
    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || !dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("日期格式错误");
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式错误");
        }
    }

    /**
     * 校验年月参数，必须是合法的 YYYY-MM
     *
     * @param yearMonthStr 年月字符串
     * @return 解析后的年月
     */
    private YearMonth parseYearMonth(String yearMonthStr) {
        if (yearMonthStr == null || !yearMonthStr.matches("\\d{4}-\\d{2}")) {
            throw new IllegalArgumentException("年月格式错误");
        }
        try {
            return YearMonth.parse(yearMonthStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("年月格式错误");
        }
    }

    /**
     * 解析用户身份，优先 Token 认证，其次秘钥认证(带缓存与失败限流)
     *
     * @param authorization Authorization请求头
     * @param key           用户秘钥
     * @param clientIp      客户端IP
     * @return 用户邮箱
     */
    private String resolveUserEmail(String authorization, String key, String clientIp) {
        // Token 认证
        if (authorization != null && !authorization.isBlank()) {
            try {
                return jwtTokenUtil.getSubject(authorization);
            } catch (RuntimeException e) {
                log.warn("Token解析失败: {}", e.getMessage());
                throw new SecurityException("Token无效");
            }
        }

        // 秘钥认证：失败次数超限直接拒绝，避免秘钥被暴力枚举
        if (key != null && !key.isBlank()) {
            String limitSubject = (clientIp != null && !clientIp.isBlank())
                    ? clientIp + ":" + MaskUtil.fingerprint(key)
                    : MaskUtil.fingerprint(key);

            if (!rateLimiterUtil.tryAcquire("key-verify", limitSubject, keyVerifyMaxFailures,
                    Duration.ofSeconds(keyVerifyWindowSeconds))) {
                log.warn("秘钥校验过于频繁，已限流: ip={}, key指纹={}", clientIp, MaskUtil.fingerprint(key));
                throw new SecurityException("请求过于频繁");
            }

            String email = userLookupCache.resolveEmailByKey(key);
            if (email == null) {
                log.warn("秘钥无效: 指纹={}", MaskUtil.fingerprint(key));
                throw new SecurityException("秘钥无效");
            }
            // 校验成功，清空失败计数
            rateLimiterUtil.reset("key-verify", limitSubject);
            return email;
        }

        throw new SecurityException("未提供认证信息");
    }
}