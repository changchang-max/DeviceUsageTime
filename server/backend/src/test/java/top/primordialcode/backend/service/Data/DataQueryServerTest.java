package top.primordialcode.backend.service.Data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.entity.AppUsageRecordEntity;
import top.primordialcode.backend.entity.AppWindowTitleEntity;
import top.primordialcode.backend.entity.DailyStatisticsEntity;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.exception.DataNotFoundException;
import top.primordialcode.backend.exception.UserNotFoundException;
import top.primordialcode.backend.mapper.HistoryDataMapper;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.utils.JwtTokenUtil;
import top.primordialcode.backend.vo.data.HistoryApplicationVO;
import top.primordialcode.backend.vo.data.HistoryDataVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("历史数据查询服务测试")
class DataQueryServerTest {

    @Mock
    private UserAuthMapper userAuthMapper;

    @Mock
    private RedisDataUploadServer redisDataUploadServer;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private HistoryDataMapper historyDataMapper;

    @InjectMocks
    private DataQueryServer dataQueryServer;

    private final String email = "test@example.com";
    private final String authorization = "Bearer valid-token";
    private final String dateStr = "2026-07-12";
    private final LocalDate date = LocalDate.of(2026, 7, 12);

    private DailyStatisticsEntity statisticsEntity() {
        DailyStatisticsEntity entity = new DailyStatisticsEntity();
        entity.setKeyboard_count(15000L);
        entity.setMouse_click_count(8000L);
        entity.setMouse_distance(new BigDecimal("150.25"));
        return entity;
    }

    private AppUsageRecordEntity appRecord(long id, String name, long totalDuration, long sessions) {
        AppUsageRecordEntity record = new AppUsageRecordEntity();
        record.setId(id);
        record.setUser_email(email);
        record.setStat_date(date);
        record.setApp_name(name);
        record.setTotal_duration(totalDuration);
        record.setSessions(sessions);
        return record;
    }

    private AppWindowTitleEntity windowTitle(long recordId, String title) {
        AppWindowTitleEntity t = new AppWindowTitleEntity();
        t.setRecord_id(recordId);
        t.setWindow_title(title);
        return t;
    }

    @Test
    @DisplayName("Token认证成功返回完整历史数据")
    void testGetHistoryDataSuccessByToken() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userAuthMapper.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(statisticsEntity());

        List<AppUsageRecordEntity> records = Arrays.asList(
                appRecord(10L, "Chrome", 10800L, 5L),
                appRecord(11L, "VSCode", 7200L, 3L));
        when(historyDataMapper.selectAppRecords(email, date)).thenReturn(records);
        when(historyDataMapper.selectTitlesByRecordIds(anyList())).thenReturn(Arrays.asList(
                windowTitle(10L, "Google搜索"),
                windowTitle(10L, "GitHub"),
                windowTitle(10L, "Stack Overflow"),
                windowTitle(11L, "main.py"),
                windowTitle(11L, "app.js")));

        HistoryDataVO vo = dataQueryServer.getHistoryData(authorization, null, dateStr);

        assertEquals(dateStr, vo.getDate());

        List<HistoryApplicationVO> apps = vo.getApplications();
        assertEquals(2, apps.size());

        HistoryApplicationVO chrome = apps.get(0);
        assertEquals("Chrome", chrome.getName());
        assertEquals(10800L, chrome.getTotalDuration());
        assertEquals(5L, chrome.getSessions());
        assertEquals(List.of("Google搜索", "GitHub", "Stack Overflow"), chrome.getWindowTitles());

        HistoryApplicationVO vscode = apps.get(1);
        assertEquals("VSCode", vscode.getName());
        assertEquals(7200L, vscode.getTotalDuration());
        assertEquals(3L, vscode.getSessions());
        assertEquals(List.of("main.py", "app.js"), vscode.getWindowTitles());

        StatisticsDTO statistics = vo.getStatistics();
        assertEquals(15000L, statistics.getKeyboardCount());
        assertEquals(8000L, statistics.getMouseClickCount());
        assertEquals(150.25, statistics.getMouseDistance());
    }

    @Test
    @DisplayName("秘钥认证成功返回历史数据")
    void testGetHistoryDataSuccessByKey() {
        String key = "aB3$xY9zK2mN7pQ";
        UserAuthEntity user = new UserAuthEntity();
        user.setUser_email(email);
        when(userAuthMapper.selectByKey(key)).thenReturn(user);
        when(userAuthMapper.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecords(email, date))
                .thenReturn(List.of(appRecord(10L, "Chrome", 10800L, 5L)));
        when(historyDataMapper.selectTitlesByRecordIds(anyList()))
                .thenReturn(List.of(windowTitle(10L, "Google搜索")));

        HistoryDataVO vo = dataQueryServer.getHistoryData(null, key, dateStr);

        assertEquals(1, vo.getApplications().size());
        assertEquals("Chrome", vo.getApplications().get(0).getName());
    }

    @Test
    @DisplayName("应用无数据但统计存在时返回空应用列表")
    void testGetHistoryDataStatisticsOnly() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userAuthMapper.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(statisticsEntity());
        when(historyDataMapper.selectAppRecords(email, date)).thenReturn(List.of());

        HistoryDataVO vo = dataQueryServer.getHistoryData(authorization, null, dateStr);

        assertTrue(vo.getApplications().isEmpty());
        assertEquals(15000L, vo.getStatistics().getKeyboardCount());
    }

    @Test
    @DisplayName("有应用数据但无统计行时返回全0统计")
    void testGetHistoryDataFillZeroStatistics() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userAuthMapper.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecords(email, date))
                .thenReturn(List.of(appRecord(10L, "Chrome", 10800L, 5L)));
        when(historyDataMapper.selectTitlesByRecordIds(anyList())).thenReturn(List.of());

        HistoryDataVO vo = dataQueryServer.getHistoryData(authorization, null, dateStr);

        assertEquals(1, vo.getApplications().size());
        assertEquals(0L, vo.getStatistics().getKeyboardCount());
        assertEquals(0.0, vo.getStatistics().getMouseDistance());
    }

    @Test
    @DisplayName("该日期无任何数据时抛出DataNotFoundException")
    void testGetHistoryDataNoData() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userAuthMapper.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecords(email, date)).thenReturn(List.of());

        assertThrows(DataNotFoundException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, dateStr));
    }

    @Test
    @DisplayName("日期格式错误抛出IllegalArgumentException")
    void testGetHistoryDataInvalidDate() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userAuthMapper.existsByEmail(email)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, "2026-7-1"));
        assertThrows(IllegalArgumentException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, "2026-13-01"));
        assertThrows(IllegalArgumentException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, "abcdef"));
        assertThrows(IllegalArgumentException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, null));
    }

    @Test
    @DisplayName("Token无效抛出SecurityException")
    void testGetHistoryDataInvalidToken() {
        when(jwtTokenUtil.getSubject(authorization)).thenThrow(new RuntimeException("token expired"));

        assertThrows(SecurityException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, dateStr));
    }

    @Test
    @DisplayName("秘钥无效抛出SecurityException")
    void testGetHistoryDataInvalidKey() {
        when(userAuthMapper.selectByKey("bad-key")).thenReturn(null);

        assertThrows(SecurityException.class,
                () -> dataQueryServer.getHistoryData(null, "bad-key", dateStr));
    }

    @Test
    @DisplayName("用户不存在抛出UserNotFoundException")
    void testGetHistoryDataUserNotExist() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userAuthMapper.existsByEmail(email)).thenReturn(false);

        assertThrows(UserNotFoundException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, dateStr));
    }
}
