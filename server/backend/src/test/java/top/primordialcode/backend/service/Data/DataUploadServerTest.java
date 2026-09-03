package top.primordialcode.backend.service.Data;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import top.primordialcode.backend.utils.JwtTokenUtil;

import java.time.Instant;
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

    @BeforeEach
    void setUp() {
        validToken = "Bearer valid.jwt.token";

        testData = new DataUploadMainDTO();
        testData.setTimestamp(Instant.parse("2026-07-12T10:30:45Z"));

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
    @DisplayName("成功接收数据")
    void testReceiveSuccess() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn("test@example.com");

        dataUploadServer.receive(validToken, testData);

        verify(jwtTokenUtil, times(1)).getSubject(validToken);
        verify(redisDataUploadServer, times(1)).updateOtherData(eq("test@example.com"), any(RedisSaveOtherDataDTO.class));
        verify(redisDataUploadServer, times(1)).updateApplications(eq("test@example.com"), anyList());
        verify(redisDataUploadServer, times(1)).updateStatistics(eq("test@example.com"), any(StatisticsDTO.class));
        verify(dataArchiveServer, times(1)).archive(eq("test@example.com"), any(Instant.class), anyList(), any(StatisticsDTO.class));
        verify(handler, times(1)).pushRealtimeUpdate(eq("test@example.com"), any(Instant.class), anyList(), any());
    }

    @Test
    @DisplayName("Token验证失败抛出SecurityException")
    void testReceiveWithInvalidToken() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenThrow(new RuntimeException("Invalid token"));

        assertThrows(SecurityException.class, () -> dataUploadServer.receive(validToken, testData));

        verify(jwtTokenUtil, times(1)).getSubject(validToken);
        verify(redisDataUploadServer, never()).updateOtherData(anyString(), any());
    }

    @Test
    @DisplayName("timestamp为null抛出IllegalArgumentException")
    void testReceiveWithNullTimestamp() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn("test@example.com");
        testData.setTimestamp(null);

        assertThrows(IllegalArgumentException.class, () -> dataUploadServer.receive(validToken, testData));

        verify(redisDataUploadServer, never()).updateOtherData(anyString(), any());
    }

    @Test
    @DisplayName("applications为空列表时不调用updateApplications")
    void testReceiveWithEmptyApplications() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn("test@example.com");
        testData.setApplications(Arrays.asList());

        dataUploadServer.receive(validToken, testData);

        verify(redisDataUploadServer, times(1)).updateOtherData(anyString(), any());
        verify(redisDataUploadServer, never()).updateApplications(anyString(), anyList());
        verify(redisDataUploadServer, times(1)).updateStatistics(anyString(), any());
    }

    @Test
    @DisplayName("statistics为null时不调用updateStatistics")
    void testReceiveWithNullStatistics() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn("test@example.com");
        testData.setStatistics(null);

        dataUploadServer.receive(validToken, testData);

        verify(redisDataUploadServer, times(1)).updateOtherData(anyString(), any());
        verify(redisDataUploadServer, times(1)).updateApplications(anyString(), anyList());
        verify(redisDataUploadServer, never()).updateStatistics(anyString(), any());
    }

    @Test
    @DisplayName("Redis序列化失败抛出RuntimeException")
    void testReceiveWithJsonProcessingException() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn("test@example.com");
        doThrow(new JsonProcessingException("Serialization failed") {})
                .when(redisDataUploadServer).updateOtherData(anyString(), any());

        assertThrows(RuntimeException.class, () -> dataUploadServer.receive(validToken, testData));
    }

    @Test
    @DisplayName("WebSocket推送失败不影响数据存储")
    void testReceiveWithWebSocketFailure() throws Exception {
        when(jwtTokenUtil.getSubject(validToken)).thenReturn("test@example.com");
        doThrow(new RuntimeException("WebSocket error"))
                .when(handler).pushRealtimeUpdate(anyString(), any(), any(), any());

        assertDoesNotThrow(() -> dataUploadServer.receive(validToken, testData));

        verify(redisDataUploadServer, times(1)).updateOtherData(anyString(), any());
        verify(handler, times(1)).pushRealtimeUpdate(anyString(), any(), any(), any());
    }
}
