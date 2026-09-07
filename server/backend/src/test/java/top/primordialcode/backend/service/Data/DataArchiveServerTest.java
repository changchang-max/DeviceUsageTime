package top.primordialcode.backend.service.Data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.entity.AppUsageRecordEntity;
import top.primordialcode.backend.entity.DataDateIndexEntity;
import top.primordialcode.backend.mapper.HistoryDataMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("历史数据归档服务测试")
class DataArchiveServerTest {

    @Mock
    private HistoryDataMapper historyDataMapper;

    @InjectMocks
    private DataArchiveServer dataArchiveServer;

    private final String email = "test@example.com";
    private final Instant timestamp = Instant.parse("2026-07-12T10:30:45Z");
    private final LocalDate date = LocalDate.of(2026, 7, 12);

    private StatisticsDTO statistics;
    private ApplicationDTO chromeApp;

    /**
     * 模拟MyBatis useGeneratedKeys：insertAppRecord时自动回填id
     */
    private void stubInsertAppRecordWithGeneratedId(long id) {
        doAnswer(invocation -> {
            AppUsageRecordEntity record = invocation.getArgument(0);
            record.setId(id);
            return 1;
        }).when(historyDataMapper).insertAppRecord(any(AppUsageRecordEntity.class));
    }

    @BeforeEach
    void setUp() {
        statistics = new StatisticsDTO();
        statistics.setKeyboardCount(15000L);
        statistics.setMouseClickCount(8000L);
        statistics.setMouseDistance(150.25);
        // 客户端程序当日运行时长(秒)，随统计数据一起归档
        statistics.setTotalDuration(28800L);

        chromeApp = new ApplicationDTO();
        chromeApp.setName("Chrome");
        chromeApp.setWindowTitle("Google搜索");
        chromeApp.setDuration(3600L);
        chromeApp.setIsActive(true);
    }

    private ApplicationDTO inactiveApp(String name, String title, long duration) {
        ApplicationDTO app = new ApplicationDTO();
        app.setName(name);
        app.setWindowTitle(title);
        app.setDuration(duration);
        app.setIsActive(false);
        return app;
    }

    @Test
    @DisplayName("首次出现+统计数据：插入记录、写入标题、维护日期索引")
    void testArchiveFirstAppearanceWithStats() {
        // 该天还没有任何索引与应用记录
        when(historyDataMapper.selectDateIndex(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecord(email, date, "Chrome")).thenReturn(null);
        // 模拟insert回填自增id
        stubInsertAppRecordWithGeneratedId(100L);

        dataArchiveServer.archive(email, timestamp,
                List.of(chromeApp), statistics);

        verify(historyDataMapper).upsertDailyStatistics(
                eq(email), eq(date), eq(15000L), eq(8000L), eq(new BigDecimal("150.25")), eq(28800L));

        ArgumentCaptor<AppUsageRecordEntity> recordCaptor =
                ArgumentCaptor.forClass(AppUsageRecordEntity.class);
        verify(historyDataMapper).insertAppRecord(recordCaptor.capture());
        AppUsageRecordEntity inserted = recordCaptor.getValue();
        assertEquals(email, inserted.getUser_email());
        assertEquals(date, inserted.getStat_date());
        assertEquals("Chrome", inserted.getApp_name());
        assertEquals(3600L, inserted.getTotal_duration());
        assertEquals(1L, inserted.getSessions());
        assertEquals("Google搜索", inserted.getLast_window_title());

        verify(historyDataMapper).insertIgnoreWindowTitle(eq(100L), eq("Google搜索"));
        verify(historyDataMapper).insertIgnoreDateIndex(email, date);
        verify(historyDataMapper).updateLastActiveApp(email, date, "Chrome");
    }

    @Test
    @DisplayName("同一应用持续前台上传：不重复累计会话")
    void testArchiveSameAppStillActive() {
        AppUsageRecordEntity record = new AppUsageRecordEntity();
        record.setId(10L);
        record.setApp_name("Chrome");
        record.setTotal_duration(3600L);
        record.setSessions(1L);
        record.setLast_window_title("Google搜索");

        DataDateIndexEntity index = new DataDateIndexEntity();
        index.setLast_active_app("Chrome");

        when(historyDataMapper.selectDateIndex(email, date)).thenReturn(index);
        when(historyDataMapper.selectAppRecord(email, date, "Chrome")).thenReturn(record);

        chromeApp.setDuration(3700L);
        dataArchiveServer.archive(email, timestamp, List.of(chromeApp), statistics);

        // 前台应用未变化，会话数不增加
        verify(historyDataMapper).updateAppRecord(eq(10L), eq(3700L), eq(0), eq("Google搜索"));
        verify(historyDataMapper, times(1)).insertIgnoreWindowTitle(eq(10L), eq("Google搜索"));
        verify(historyDataMapper).updateLastActiveApp(email, date, "Chrome");
        verify(historyDataMapper, never()).insertAppRecord(any());
    }

    @Test
    @DisplayName("切换到另一应用：新前台应用会话数+1")
    void testArchiveSwitchToAnotherApp() {
        AppUsageRecordEntity vscode = new AppUsageRecordEntity();
        vscode.setId(11L);
        vscode.setApp_name("VSCode");
        vscode.setSessions(1L);
        vscode.setLast_window_title("main.py");

        DataDateIndexEntity index = new DataDateIndexEntity();
        index.setLast_active_app("Chrome");

        when(historyDataMapper.selectDateIndex(email, date)).thenReturn(index);
        when(historyDataMapper.selectAppRecord(email, date, "VSCode")).thenReturn(vscode);

        ApplicationDTO vscodeApp = new ApplicationDTO();
        vscodeApp.setName("VSCode");
        vscodeApp.setWindowTitle("app.js");
        vscodeApp.setDuration(7200L);
        vscodeApp.setIsActive(true);

        dataArchiveServer.archive(email, timestamp, List.of(vscodeApp), statistics);

        verify(historyDataMapper).updateAppRecord(eq(11L), eq(7200L), eq(1), eq("app.js"));
        verify(historyDataMapper).insertIgnoreWindowTitle(eq(11L), eq("app.js"));
        verify(historyDataMapper).updateLastActiveApp(email, date, "VSCode");
    }

    @Test
    @DisplayName("非前台应用上传：不增加会话、不更新前台跟踪标题")
    void testArchiveInactiveApp() {
        AppUsageRecordEntity record = new AppUsageRecordEntity();
        record.setId(10L);
        record.setApp_name("Chrome");
        record.setSessions(1L);
        record.setLast_window_title("Google搜索");

        DataDateIndexEntity index = new DataDateIndexEntity();
        index.setLast_active_app("Chrome");

        when(historyDataMapper.selectDateIndex(email, date)).thenReturn(index);
        when(historyDataMapper.selectAppRecord(email, date, "Chrome")).thenReturn(record);

        ApplicationDTO app = inactiveApp("Chrome", "Google搜索", 3600L);
        dataArchiveServer.archive(email, timestamp, List.of(app), statistics);

        // add_sessions=0，标题参数为null(前台跟踪标题保持不变)
        verify(historyDataMapper).updateAppRecord(eq(10L), eq(3600L), eq(0), isNull());
        verify(historyDataMapper).insertIgnoreWindowTitle(eq(10L), eq("Google搜索"));
        verify(historyDataMapper).updateLastActiveApp(email, date, "Chrome");
    }

    @Test
    @DisplayName("statistics为null时不写每日统计")
    void testArchiveWithoutStatistics() {
        when(historyDataMapper.selectDateIndex(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecord(email, date, "Chrome")).thenReturn(null);

        dataArchiveServer.archive(email, timestamp, List.of(chromeApp), null);

        verify(historyDataMapper, never()).upsertDailyStatistics(anyString(), any(), any(), any(), any(), any());
        verify(historyDataMapper).insertAppRecord(any(AppUsageRecordEntity.class));
        verify(historyDataMapper).insertIgnoreDateIndex(email, date);
    }

    @Test
    @DisplayName("仅统计无应用：不查询应用记录")
    void testArchiveStatisticsOnly() {
        when(historyDataMapper.selectDateIndex(email, date)).thenReturn(null);

        dataArchiveServer.archive(email, timestamp, Collections.emptyList(), statistics);

        verify(historyDataMapper).upsertDailyStatistics(anyString(), any(), any(), any(), any(), any());
        verify(historyDataMapper, never()).selectAppRecord(anyString(), any(), any());
        verify(historyDataMapper).insertIgnoreDateIndex(email, date);
        // 没有前台应用，无需更新前台跟踪
        verify(historyDataMapper, never()).updateLastActiveApp(anyString(), any(), any());
    }

    @Test
    @DisplayName("统计数据缺失totalDuration时原样传null(由SQL保留已有值/新行补0)")
    void testArchiveStatisticsWithoutTotalDuration() {
        when(historyDataMapper.selectDateIndex(email, date)).thenReturn(null);
        statistics.setTotalDuration(null);

        dataArchiveServer.archive(email, timestamp, Collections.emptyList(), statistics);

        verify(historyDataMapper).upsertDailyStatistics(
                eq(email), eq(date), eq(15000L), eq(8000L), eq(new BigDecimal("150.25")), isNull());
        verify(historyDataMapper).insertIgnoreDateIndex(email, date);
    }

    @Test
    @DisplayName("窗口标题为空时不写标题表")
    void testArchiveBlankWindowTitle() {
        when(historyDataMapper.selectDateIndex(email, date)).thenReturn(null);
        when(historyDataMapper.selectAppRecord(email, date, "Chrome")).thenReturn(null);

        chromeApp.setWindowTitle(null);
        dataArchiveServer.archive(email, timestamp, List.of(chromeApp), statistics);

        verify(historyDataMapper, never()).insertIgnoreWindowTitle(anyLong(), anyString());
    }

    @Test
    @DisplayName("timestamp为null时直接忽略归档")
    void testArchiveNullTimestamp() {
        dataArchiveServer.archive(email, null, List.of(chromeApp), statistics);

        verify(historyDataMapper, never()).upsertDailyStatistics(anyString(), any(), any(), any(), any(), any());
        verify(historyDataMapper, never()).insertAppRecord(any());
        verify(historyDataMapper, never()).insertIgnoreDateIndex(anyString(), any());
    }
}
