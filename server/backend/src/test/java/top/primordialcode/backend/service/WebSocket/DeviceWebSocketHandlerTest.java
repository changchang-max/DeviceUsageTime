package top.primordialcode.backend.service.WebSocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import top.primordialcode.backend.dto.DataUpload.ApplicationDTO;
import top.primordialcode.backend.dto.DataUpload.StatisticsDTO;
import top.primordialcode.backend.mapper.UserAuthMapper;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WebSocket协议处理器单元测试
 * 验证 docs/前后端API文档.md 第7章 消息流程:
 * connected / subscribe / unsubscribe / ping→pong / error / realtime_update
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("设备WebSocket处理器测试")
class DeviceWebSocketHandlerTest {

    @Mock
    private UserAuthMapper userAuthMapper;

    private ObjectMapper objectMapper;
    private DeviceWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        // 与Spring Boot默认配置一致: 支持Java8时间序列化为ISO字符串
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        handler = new DeviceWebSocketHandler(objectMapper, userAuthMapper);
    }

    /**
     * 创建一个模拟连接
     */
    private WebSocketSession mockSession(Map<String, Object> attributes) {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-" + System.nanoTime());
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    /**
     * 建立连接(带指定认证信息的Session属性)
     */
    private WebSocketSession connect(String userId, String authMode) throws Exception {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(DeviceWebSocketHandler.ATTR_USER_ID, userId);
        attributes.put(DeviceWebSocketHandler.ATTR_AUTH_MODE, authMode);
        WebSocketSession session = mockSession(attributes);
        handler.afterConnectionEstablished(session);
        return session;
    }

    /**
     * 捕获最后一次发送的消息
     */
    private JsonNode lastMessage(WebSocketSession session) throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return objectMapper.readTree(captor.getValue().getPayload());
    }

    private ApplicationDTO application(String name, boolean active) {
        ApplicationDTO app = new ApplicationDTO();
        app.setName(name);
        app.setWindowTitle("Window-" + name);
        app.setDuration(3600L);
        app.setIsActive(active);
        return app;
    }

    private StatisticsDTO statistics() {
        StatisticsDTO stats = new StatisticsDTO();
        stats.setKeyboardCount(1250L);
        stats.setMouseClickCount(856L);
        stats.setMouseDistance(23.01);
        return stats;
    }

    @Test
    @DisplayName("连接建立后自动订阅并发送connected消息")
    void testConnectionSendsConnectedAndAutoSubscribes() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);

        JsonNode connected = lastMessage(session);
        assertEquals("connected", connected.path("type").asText());
        assertEquals("owner@example.com", connected.path("userId").asText());
    }

    @Test
    @DisplayName("秘钥查看者连接时不返回userId")
    void testKeyConnectionHidesUserId() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_KEY);

        JsonNode connected = lastMessage(session);
        assertEquals("connected", connected.path("type").asText());
        assertTrue(connected.path("userId").isNull());
    }

    @Test
    @DisplayName("无认证身份时拒绝连接")
    void testConnectionWithoutIdentityClosed() throws Exception {
        // 不使用mockSession: 无身份场景不会用到isOpen/getId桩
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(session);

        verify(session).close(CloseStatus.NOT_ACCEPTABLE);
        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("收到ping后响应pong")
    void testPingRespondsPong() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"ping\"}"));

        JsonNode pong = lastMessage(session);
        assertEquals("pong", pong.path("type").asText());
        assertFalse(pong.path("timestamp").isMissingNode());
    }

    @Test
    @DisplayName("推送realtime_update给订阅者(完整快照格式)")
    void testPushRealtimeUpdate() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);

        // 服务端在客户端上传后推送完整实时快照
        handler.pushRealtimeUpdate(
                "owner@example.com",
                Instant.parse("2026-07-12T10:30:45Z"),
                Arrays.asList(application("Chrome", true), application("VSCode", false)),
                statistics()
        );

        JsonNode update = lastMessage(session);
        assertEquals("realtime_update", update.path("type").asText());
        assertEquals("owner@example.com", update.path("userId").asText());
        assertEquals("2026-07-12T10:30:45Z", update.path("timestamp").asText());
        assertEquals(2, update.path("data").path("applications").size());
        assertEquals("Chrome", update.path("data").path("applications").get(0).path("name").asText());
        assertEquals(1250, update.path("data").path("statistics").path("keyboardCount").asInt());
    }

    @Test
    @DisplayName("取消订阅后不再收到推送")
    void testUnsubscribeStopsPush() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);

        handler.pushRealtimeUpdate("owner@example.com", Instant.now(),
                Arrays.asList(application("Chrome", true)), null);
        // 取消订阅
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"unsubscribe\",\"userId\":\"owner@example.com\"}"));
        // 再次推送: 不应再有消息发出
        handler.pushRealtimeUpdate("owner@example.com", Instant.now(),
                Arrays.asList(application("Chrome", true)), null);

        verify(session, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("订阅无权限用户返回403")
    void testSubscribeForbidden() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_KEY);
        // 目标用户存在但不是秘钥持有者
        when(userAuthMapper.existsByEmail("other@example.com")).thenReturn(true);

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"subscribe\",\"userId\":\"other@example.com\"}"));

        JsonNode error = lastMessage(session);
        assertEquals("error", error.path("type").asText());
        assertEquals(403, error.path("code").asInt());
    }

    @Test
    @DisplayName("订阅不存在的用户返回404")
    void testSubscribeUserNotFound() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);
        // existsByEmail默认false
        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"subscribe\",\"userId\":\"ghost@example.com\"}"));

        JsonNode error = lastMessage(session);
        assertEquals("error", error.path("type").asText());
        assertEquals(404, error.path("code").asInt());
    }

    @Test
    @DisplayName("订阅授权用户(大小写不敏感)成功且不查库")
    void testSubscribeAuthorizedUser() throws Exception {
        WebSocketSession session = connect("Owner@Example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);

        handler.handleTextMessage(session,
                new TextMessage("{\"type\":\"subscribe\",\"userId\":\"owner@example.com\"}"));

        // 授权订阅不应触发数据库查询，也不应发送任何错误
        verify(userAuthMapper, never()).existsByEmail(anyString());
        verify(session, times(1)).sendMessage(any(TextMessage.class)); // 仅connected消息
    }

    @Test
    @DisplayName("非法JSON消息返回400错误")
    void testMalformedMessageReturnsError() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);

        handler.handleTextMessage(session, new TextMessage("not-a-json"));

        JsonNode error = lastMessage(session);
        assertEquals("error", error.path("type").asText());
        assertEquals(400, error.path("code").asInt());
    }

    @Test
    @DisplayName("未知消息类型返回400错误")
    void testUnknownTypeReturnsError() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);

        handler.handleTextMessage(session, new TextMessage("{\"type\":\"unknown\"}"));

        JsonNode error = lastMessage(session);
        assertEquals("error", error.path("type").asText());
        assertEquals(400, error.path("code").asInt());
    }

    @Test
    @DisplayName("连接关闭后清理订阅关系")
    void testCloseCleansSubscriptions() throws Exception {
        WebSocketSession session = connect("owner@example.com", DeviceWebSocketHandler.AUTH_MODE_TOKEN);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        // 清理后推送不应发送消息(仅connected一条)
        handler.pushRealtimeUpdate("owner@example.com", Instant.now(),
                Arrays.asList(application("Chrome", true)), null);

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}
