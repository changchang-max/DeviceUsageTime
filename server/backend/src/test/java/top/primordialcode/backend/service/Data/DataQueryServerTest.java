package top.primordialcode.backend.service.Data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.utils.JwtTokenUtil;
import top.primordialcode.backend.utils.RateLimiterUtil;
import top.primordialcode.backend.utils.UserLookupCache;
import top.primordialcode.backend.vo.data.HistoryApplicationVO;
import top.primordialcode.backend.vo.data.HistoryDataVO;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("历史数据查询服务测试")
class DataQueryServerTest {

    @Mock
    private UserLookupCache userLookupCache;

    @Mock
    private RedisDataUploadServer redisDataUploadServer;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private HistoryDataMapper historyDataMapper;

    @Mock
    private RateLimiterUtil rateLimiterUtil;

    @InjectMocks
    private DataQueryServer dataQueryServer;

    private final String email = "test@example.com";
    private final String authorization = "Bearer valid-token";
    private final String dateStr = "2026-07-12";
    private final LocalDate date = LocalDate.of(2026, 7, 12);
    private final String clientIp = "127.0.0.1";

    @org.junit.jupiter.api.BeforeEach
    void setUpRateLimiter() {
        // 默认放行；个别用例会覆盖为"超限"
        lenient().when(rateLimiterUtil.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(true);
    }

    private DailyStatisticsEntity statisticsEntity() {
        DailyStatisticsEntity entity = new DailyStatisticsEntity();
        entity.setKeyboard_count(15000L);
        entity.setMouse_click_count(8000L);
        entity.setMouse_distance(new BigDecimal("150.25"));
        // 客户端程序当日运行时长(秒)
        entity.setTotal_duration(28800L);
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
    @DisplayName("Token认证成功返回完整历史数据(含窗口标题)")
    void testGetHistoryDataSuccessByToken() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(true);
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

        HistoryDataVO vo = dataQueryServer.getHistoryData(authorization, null, clientIp, dateStr, true);

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
        assertEquals(28800L, statistics.getTotalDuration());
    }

    @Test
    @DisplayName("默认不返回窗口标题(懒加载)，且不查询标题表")
    void testGetHistoryDataWithoutTitles() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(statisticsEntity());
        when(historyDataMapper.selectAppRecords(email, date))
                .thenReturn(List.of(appRecord(10L, "Chrome", 10800L, 5L)));

        HistoryDataVO vo = dataQueryServer.getHistoryData(authorization, null, clientIp, dateStr, false);

        assertEquals(1, vo.getApplications().size());
        assertTrue(vo.getApplications().get(0).getWindowTitles().isEmpty());
        verify(historyDataMapper, never()).selectTitlesByRecordIds(anyList());
    }

    @Test
    @DisplayName("秘钥认证成功返回历史数据")
    void testGetHistoryDataSuccessByKey() {
        String key = "aB3$xY9zK2mN7pQ";
        when(userLookupCache.resolveEmailByKey(key)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecords(email, date))
                .thenReturn(List.of(appRecord(10L, "Chrome", 10800L, 5L)));
        when(historyDataMapper.selectTitlesByRecordIds(anyList()))
                .thenReturn(List.of(windowTitle(10L, "Google搜索")));

        HistoryDataVO vo = dataQueryServer.getHistoryData(null, key, clientIp, dateStr, true);

        assertEquals(1, vo.getApplications().size());
        assertEquals("Chrome", vo.getApplications().get(0).getName());
        // 成功后应重置限流计数
        verify(rateLimiterUtil).reset(eq("key-verify"), anyString());
    }

    @Test
    @DisplayName("应用无数据但统计存在时返回空应用列表")
    void testGetHistoryDataStatisticsOnly() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(statisticsEntity());
        when(historyDataMapper.selectAppRecords(email, date)).thenReturn(List.of());

        HistoryDataVO vo = dataQueryServer.getHistoryData(authorization, null, clientIp, dateStr, false);

        assertTrue(vo.getApplications().isEmpty());
        assertEquals(15000L, vo.getStatistics().getKeyboardCount());
    }

    @Test
    @DisplayName("有应用数据但无统计行时返回全0统计")
    void testGetHistoryDataFillZeroStatistics() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecords(email, date))
                .thenReturn(List.of(appRecord(10L, "Chrome", 10800L, 5L)));

        HistoryDataVO vo = dataQueryServer.getHistoryData(authorization, null, clientIp, dateStr, false);

        assertEquals(1, vo.getApplications().size());
        assertEquals(0L, vo.getStatistics().getKeyboardCount());
        assertEquals(0.0, vo.getStatistics().getMouseDistance());
        assertEquals(0L, vo.getStatistics().getTotalDuration());
    }

    @Test
    @DisplayName("该日期无任何数据时抛出DataNotFoundException")
    void testGetHistoryDataNoData() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(true);
        when(historyDataMapper.selectDailyStatistics(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecords(email, date)).thenReturn(List.of());

        assertThrows(DataNotFoundException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, clientIp, dateStr, false));
    }

    @Test
    @DisplayName("日期格式错误抛出IllegalArgumentException")
    void testGetHistoryDataInvalidDate() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, clientIp, "2026-7-1", false));
        assertThrows(IllegalArgumentException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, clientIp, "2026-13-01", false));
        assertThrows(IllegalArgumentException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, clientIp, "abcdef", false));
        assertThrows(IllegalArgumentException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, clientIp, null, false));
    }

    @Test
    @DisplayName("Token无效抛出SecurityException")
    void testGetHistoryDataInvalidToken() {
        when(jwtTokenUtil.getSubject(authorization)).thenThrow(new RuntimeException("token expired"));

        assertThrows(SecurityException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, clientIp, dateStr, false));
    }

    @Test
    @DisplayName("秘钥无效抛出SecurityException(不再明文记录秘钥)")
    void testGetHistoryDataInvalidKey() {
        when(userLookupCache.resolveEmailByKey("bad-key")).thenReturn(null);

        assertThrows(SecurityException.class,
                () -> dataQueryServer.getHistoryData(null, "bad-key", clientIp, dateStr, false));
    }

    @Test
    @DisplayName("秘钥校验超过限流阈值抛出SecurityException且不查缓存")
    void testGetHistoryDataRateLimited() {
        when(rateLimiterUtil.tryAcquire(anyString(), anyString(), anyInt(), any(Duration.class)))
                .thenReturn(false);

        assertThrows(SecurityException.class,
                () -> dataQueryServer.getHistoryData(null, "some-key", clientIp, dateStr, false));
        verify(userLookupCache, never()).resolveEmailByKey(anyString());
    }

    @Test
    @DisplayName("用户不存在抛出UserNotFoundException")
    void testGetHistoryDataUserNotExist() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(false);

        assertThrows(UserNotFoundException.class,
                () -> dataQueryServer.getHistoryData(authorization, null, clientIp, dateStr, false));
    }

    @Test
    @DisplayName("实时数据读取一次快照并按应用时长降序返回")
    void testGetRealtimeDataSortsApplications() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(userLookupCache.existsByEmail(email)).thenReturn(true);

        top.primordialcode.backend.dto.DataUpload.ApplicationDTO shortApp =
                new top.primordialcode.backend.dto.DataUpload.ApplicationDTO();
        shortApp.setName("VSCode");
        shortApp.setDuration(600L);
        shortApp.setIsActive(false);

        top.primordialcode.backend.dto.DataUpload.ApplicationDTO longApp =
                new top.primordialcode.backend.dto.DataUpload.ApplicationDTO();
        longApp.setName("Chrome");
        longApp.setDuration(3600L);
        longApp.setIsActive(true);

        RedisDataUploadServer.Snapshot snapshot = new RedisDataUploadServer.Snapshot();
        snapshot.setApplications(Arrays.asList(shortApp, longApp));
        when(redisDataUploadServer.readSnapshot(eq(email), any(LocalDate.class))).thenReturn(snapshot);

        var vo = dataQueryServer.getRealtimeData(authorization, null, clientIp);

        assertEquals(email, vo.getUserId());
        assertEquals(2, vo.getApplications().size());
        assertEquals("Chrome", vo.getApplications().get(0).getName());
        // 只读一次快照，不再分别读 applications/statistics/otherData
        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(redisDataUploadServer, times(1)).readSnapshot(eq(email), captor.capture());
    }

    @Test
    @DisplayName("窗口标题懒加载接口按应用返回标题")
    void testGetWindowTitles() {
        when(jwtTokenUtil.getSubject(authorization)).thenReturn(email);
        when(historyDataMapper.selectAppRecord(email, date, "Chrome"))
                .thenReturn(appRecord(10L, "Chrome", 10800L, 5L));
        when(historyDataMapper.selectTitlesByRecordIds(List.of(10L)))
                .thenReturn(List.of(windowTitle(10L, "GitHub")));

        List<String> titles = dataQueryServer.getWindowTitles(authorization, null, clientIp, dateStr, "Chrome");

        assertEquals(List.of("GitHub"), titles);
    }
}