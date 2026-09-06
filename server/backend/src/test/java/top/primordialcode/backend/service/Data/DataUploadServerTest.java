package top.primordialcode.backend.service.Data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.DataUploadMainDTO;
import top.primordialcode.backend.dto.DataUpload.RedisSaveOtherDataDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.service.Redis.RedisDataUploadServer;
import top.primordialcode.backend.service.WebSocket.DeviceWebSocketHandler;
import top.primordialcode.backend.utils.DataDateUtil;
import top.primordialcode.backend.utils.JwtTokenUtil;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据上传服务测试")
class DataUploadServerTest {

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private RedisDataUploadServer redisDataUploadServer;

    @Mock
    private DeviceWebSocketHandler handler;

    @Mock
    private DataArchiveServer dataArchiveServer;

    @InjectMocks
    private DataUploadServer dataUploadServer;

    private DataUploadMainDTO testData;
    private String validToken;
    private String email;

    /** 上传时间戳(取当前时间,保证归属日期=今天,实时推送路径可测) */
    private Instant timestamp;
    /** 由timestamp换算出的数据归属日期 */
    private LocalDate date;

    @BeforeEach
    void setUp() {
        validToken = "Bearer valid.jwt.token";
        email = "test@example.com";
        timestamp = Instant.now();
        date = DataDateUtil.toDataDate(timestamp);

        testData = new DataUploadMainDTO();
        testData.setTimestamp(timestamp);

        ApplicationDTO app = new ApplicationDTO();
        app.setName("Chrome");
        app.setWindowTitle("Google");
        app.setDuration(3600L);
        app.setIsActive(true);

        testData.setApplications(Arrays.asList(app));

        StatisticsDTO stats = new StatisticsDTO();
        stats.setKeyboardCount(1250L);
        stats.setMouseClickCount(856L);
        stats.setMouseDistance(23.01);

        testData.setStatistics(stats);
    }

    @Test
    @DisplayName("成功接收今日数据: 按日期入库并推送实时更新")
    void testReceiveSuccess() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn(email);

        dataUploadServer.receive(validToken, testData);

        verify(jwtTokenUtil, times(1)).getSubject(validToken);
        verify(redisDataUploadServer, times(1))
                .updateOtherData(eq(email), eq(date), any(RedisSaveOtherDataDTO.class));
        verify(redisDataUploadServer, times(1))
                .updateApplications(eq(email), eq(date), anyList());
        verify(redisDataUploadServer, times(1))
                .updateStatistics(eq(email), eq(date), any(StatisticsDTO.class));
        verify(dataArchiveServer, times(1))
                .archive(eq(email), any(Instant.class), anyList(), any(StatisticsDTO.class));
        verify(handler, times(1)).pushRealtimeUpdate(eq(email), any(Instant.class), anyList(), any());
    }

    @Test
    @DisplayName("上传非今日数据: 正常按该日期入库归档, 但不推送实时更新")
    void testReceiveOtherDateDoesNotPushRealtime() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn(email);

        // 时间戳向前平移48小时, 归属日期恒等于"今天-2天", 与实时推送无关
        Instant otherDateTimestamp = timestamp.minus(Duration.ofDays(2));
        LocalDate otherDate = DataDateUtil.toDataDate(otherDateTimestamp);
        testData.setTimestamp(otherDateTimestamp);

        dataUploadServer.receive(validToken, testData);

        verify(redisDataUploadServer, times(1))
                .updateOtherData(eq(email), eq(otherDate), any(RedisSaveOtherDataDTO.class));
        verify(redisDataUploadServer, times(1))
                .updateApplications(eq(email), eq(otherDate), anyList());
        verify(redisDataUploadServer, times(1))
                .updateStatistics(eq(email), eq(otherDate), any(StatisticsDTO.class));
        verify(dataArchiveServer, times(1))
                .archive(eq(email), eq(otherDateTimestamp), anyList(), any(StatisticsDTO.class));
        // 非今日数据不允许推送实时更新, 避免污染查看者正在看的今日实时页
        verify(handler, never()).pushRealtimeUpdate(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Token验证失败抛出SecurityException")
    void testReceiveWithInvalidToken() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenThrow(new RuntimeException("Invalid token"));

        assertThrows(SecurityException.class, () -> dataUploadServer.receive(validToken, testData));

        verify(jwtTokenUtil, times(1)).getSubject(validToken);
        verify(redisDataUploadServer, never()).updateOtherData(anyString(), any(), any());
    }

    @Test
    @DisplayName("timestamp为null抛出IllegalArgumentException")
    void testReceiveWithNullTimestamp() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn(email);
        testData.setTimestamp(null);

        assertThrows(IllegalArgumentException.class, () -> dataUploadServer.receive(validToken, testData));

        verify(redisDataUploadServer, never()).updateOtherData(anyString(), any(), any());
    }

    @Test
    @DisplayName("applications为空列表时不调用updateApplications")
    void testReceiveWithEmptyApplications() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn(email);
        testData.setApplications(Arrays.asList());

        dataUploadServer.receive(validToken, testData);

        verify(redisDataUploadServer, times(1)).updateOtherData(anyString(), any(), any());
        verify(redisDataUploadServer, never()).updateApplications(anyString(), any(), anyList());
        verify(redisDataUploadServer, times(1)).updateStatistics(anyString(), any(), any());
    }

    @Test
    @DisplayName("statistics为null时不调用updateStatistics")
    void testReceiveWithNullStatistics() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn(email);
        testData.setStatistics(null);

        dataUploadServer.receive(validToken, testData);

        verify(redisDataUploadServer, times(1)).updateOtherData(anyString(), any(), any());
        verify(redisDataUploadServer, times(1)).updateApplications(anyString(), any(), anyList());
        verify(redisDataUploadServer, never()).updateStatistics(anyString(), any(), any());
    }

    @Test
    @DisplayName("Redis写入失败抛出RuntimeException")
    void testReceiveWithRedisFailure() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn(email);
        doThrow(new RuntimeException("Redis写入失败"))
                .when(redisDataUploadServer).updateOtherData(anyString(), any(), any());

        assertThrows(RuntimeException.class, () -> dataUploadServer.receive(validToken, testData));
    }

    @Test
    @DisplayName("WebSocket推送失败不影响数据存储")
    void testReceiveWithWebSocketFailure() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn(email);
        doThrow(new RuntimeException("WebSocket error"))
                .when(handler).pushRealtimeUpdate(anyString(), any(), any(), any());

        assertDoesNotThrow(() -> dataUploadServer.receive(validToken, testData));

        verify(redisDataUploadServer, times(1)).updateOtherData(anyString(), any(), any());
        verify(handler, times(1)).pushRealtimeUpdate(anyString(), any(), any(), any());
    }
}
