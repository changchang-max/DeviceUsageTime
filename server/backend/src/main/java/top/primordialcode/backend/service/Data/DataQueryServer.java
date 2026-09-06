package top.primordialcode.backend.service.Data;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.entity.AppUsageRecordEntity;
import top.primordialcode.backend.entity.AppWindowTitleEntity;
import top.primordialcode.backend.entity.DailyStatisticsEntity;
import top.primordialcode.backend.entity.DataDateIndexEntity;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.exception.DataNotFoundException;
import top.primordialcode.backend.exception.UserNotFoundException;
import top.primordialcode.backend.mapper.HistoryDataMapper;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.utils.DataDateUtil;
import top.primordialcode.backend.utils.JwtTokenUtil;
import top.primordialcode.backend.vo.data.DataDatesVO;
import top.primordialcode.backend.vo.data.HistoryApplicationVO;
import top.primordialcode.backend.vo.data.HistoryDataVO;
import top.primordialcode.backend.vo.data.RealtimeDataVO;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DataQueryServer {

    @Autowired
    UserAuthMapper userAuthMapper;

    @Autowired
    RedisDataUploadServer redisDataUploadServer;

    @Autowired
    JwtTokenUtil jwtTokenUtil;

    @Autowired
    HistoryDataMapper historyDataMapper;

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
            // 实时数据即"今日"的热数据快照: 以统一时区(Asia/Shanghai)当天的日期分桶读取。
            // 客户端补传/误传的其他日期数据归档在MySQL中,不会出现在这里。
            LocalDate today = DataDateUtil.today();

            List<ApplicationDTO> applications =
                    redisDataUploadServer.getApplications(userEmail, today);
            StatisticsDTO statistics =
                    redisDataUploadServer.getStatistics(userEmail, today);
            RedisSaveOtherDataDTO otherData =
                    redisDataUploadServer.getOtherData(userEmail, today);

            Instant timestamp = (otherData != null)
                    ? otherData.getTimestamp()
                    : null;

            // 组装VO
            RealtimeDataVO vo = new RealtimeDataVO();
            vo.setUserId(userEmail);
            vo.setTimestamp(timestamp);
            vo.setDate(today.toString());
            vo.setApplications(applications);
            vo.setStatistics(statistics);

            return vo;
        } catch (Exception e) {
            throw new RuntimeException("读取实时数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取指定日期的历史数据(MySQL冷数据)
     * @param authorization Authorization请求头(可选，Bearer token)
     * @param key 用户秘钥(可选)
     * @param dateStr 日期(YYYY-MM-DD)
     * @return 历史数据VO
     */
    public HistoryDataVO getHistoryData(String authorization, String key, String dateStr) {
        // 解析用户身份(Token认证或秘钥认证)
        String userEmail = resolveUserEmail(authorization, key);

        // 校验用户是否存在
        if (!userAuthMapper.existsByEmail(userEmail)) {
            log.warn("用户不存在，邮箱：{}", userEmail);
            throw new UserNotFoundException("用户不存在");
        }

        // 校验日期格式
        LocalDate date = parseDate(dateStr);

        // 查询冷数据
        DailyStatisticsEntity statisticsEntity =
                historyDataMapper.selectDailyStatistics(userEmail, date);
        List<AppUsageRecordEntity> records =
                historyDataMapper.selectAppRecords(userEmail, date);

        // 无任何数据
        if ((records == null || records.isEmpty()) && statisticsEntity == null) {
            throw new DataNotFoundException("该日期无数据");
        }

        // 按应用聚合窗口标题
        Map<Long, List<String>> titlesByRecordId = new HashMap<>();
        if (records != null && !records.isEmpty()) {
            List<Long> recordIds = new ArrayList<>();
            for (AppUsageRecordEntity record : records) {
                recordIds.add(record.getId());
            }
            List<AppWindowTitleEntity> windowTitles =
                    historyDataMapper.selectTitlesByRecordIds(recordIds);
            for (AppWindowTitleEntity title : windowTitles) {
                titlesByRecordId
                        .computeIfAbsent(title.getRecord_id(), k -> new ArrayList<>())
                        .add(title.getWindow_title());
            }
        }

        // 组装VO(应用记录已按总时长降序排列)
        HistoryDataVO vo = new HistoryDataVO();
        vo.setDate(date.toString());

        List<HistoryApplicationVO> applicationVOs = new ArrayList<>();
        if (records != null) {
            for (AppUsageRecordEntity record : records) {
                HistoryApplicationVO appVO = new HistoryApplicationVO();
                appVO.setName(record.getApp_name());
                appVO.setTotalDuration(
                        record.getTotal_duration() != null ? record.getTotal_duration() : 0L);
                appVO.setSessions(record.getSessions() != null ? record.getSessions() : 0L);
                List<String> titles = titlesByRecordId.get(record.getId());
                appVO.setWindowTitles(titles != null ? titles : new ArrayList<>());
                applicationVOs.add(appVO);
            }
        }
        vo.setApplications(applicationVOs);

        // 统计：有应用数据但无统计行时返回全0，避免前端处理空值
        vo.setStatistics(toStatisticsVO(statisticsEntity));

        return vo;
    }

    /**
     * 获取某个月份内有数据记录的日期列表(基于data_date_index日期索引)
     * @param authorization Authorization请求头(可选，Bearer token)
     * @param key 用户秘钥(可选)
     * @param yearMonthStr 年月(YYYY-MM)
     * @return 有数据日期列表VO
     */
    public DataDatesVO getDataDates(String authorization, String key, String yearMonthStr) {
        // 解析用户身份(Token认证或秘钥认证)
        String userEmail = resolveUserEmail(authorization, key);

        // 校验用户是否存在
        if (!userAuthMapper.existsByEmail(userEmail)) {
            log.warn("用户不存在，邮箱：{}", userEmail);
            throw new UserNotFoundException("用户不存在");
        }

        // 校验年月格式并计算该月日期范围
        YearMonth yearMonth = parseYearMonth(yearMonthStr);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        // 查询该月内有数据记录的日期索引(按日期升序)
        List<DataDateIndexEntity> dateIndexes =
                historyDataMapper.selectDateIndexesInRange(userEmail, startDate, endDate);

        // 组装VO
        List<String> dates = new ArrayList<>();
        if (dateIndexes != null) {
            for (DataDateIndexEntity dateIndex : dateIndexes) {
                dates.add(dateIndex.getStat_date().toString());
            }
        }

        DataDatesVO vo = new DataDatesVO();
        vo.setYearMonth(yearMonth.toString());
        vo.setDates(dates);
        return vo;
    }

    /**
     * 将每日统计数据实体转换为VO(缺省字段补0)
     */
    private StatisticsDTO toStatisticsVO(DailyStatisticsEntity entity) {
        StatisticsDTO statistics = new StatisticsDTO();
        statistics.setKeyboardCount(
                entity != null && entity.getKeyboard_count() != null
                        ? entity.getKeyboard_count() : 0L);
        statistics.setMouseClickCount(
                entity != null && entity.getMouse_click_count() != null
                        ? entity.getMouse_click_count() : 0L);
        statistics.setMouseDistance(
                entity != null && entity.getMouse_distance() != null
                        ? entity.getMouse_distance().doubleValue() : 0.0);
        return statistics;
    }

    /**
     * 校验日期参数，必须是合法的 YYYY-MM-DD
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
