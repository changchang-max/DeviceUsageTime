package top.primordialcode.backend.service.WebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.utils.JwtUtil;
import top.primordialcode.backend.vo.websocket.WsErrorMessage;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket握手拦截器
 *
 * 支持两种连接认证方式(URL查询参数):
 * 1. Token认证: ws://.../ws/device?token={token}
 * 2. 秘钥认证:   ws://.../ws/device?key={secretKey}
 *
 * 认证通过后把「被授权查看的用户标识」写入Session属性，
 * 供 DeviceWebSocketHandler 使用。认证失败则拒绝握手并返回401。
 */
@Slf4j
@Component
public class JwtHandshakeInterceptor
        implements HandshakeInterceptor {

    /**
     * 查询参数名: token
     */
    private static final String QUERY_TOKEN = "token";

    /**
     * 查询参数名: key
     */
    private static final String QUERY_KEY = "key";

    private final JwtUtil jwtUtil;
    private final UserAuthMapper userAuthMapper;
    private final ObjectMapper objectMapper;

    public JwtHandshakeInterceptor(
            JwtUtil jwtUtil,
            UserAuthMapper userAuthMapper,
            ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userAuthMapper = userAuthMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 握手之前: 解析并校验 token 或 key
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        // 解析URL查询参数(使用原始Query，避免二次解码)
        Map<String, String> params = parseQuery(request.getURI().getRawQuery());

        String token = params.get(QUERY_TOKEN);
        String key = params.get(QUERY_KEY);

        // ========== Token认证 ==========
        if (token != null && !token.isBlank()) {
            try {
                // 解析JWT，subject为用户邮箱(本系统中以邮箱作为全局唯一用户标识)
                String email = jwtUtil.parse(token);

                // 保存被授权查看的用户标识与认证方式
                attributes.put(DeviceWebSocketHandler.ATTR_USER_ID, email);
                attributes.put(
                        DeviceWebSocketHandler.ATTR_AUTH_MODE,
                        DeviceWebSocketHandler.AUTH_MODE_TOKEN);

                return true;
            } catch (ExpiredJwtException e) {
                log.warn("WebSocket握手失败，JWT token已过期: {}", e.getMessage());
                return reject(response, 401, "Token已过期");
            } catch (JwtException e) {
                log.warn("WebSocket握手失败，JWT token验证失败: {}", e.getMessage());
                return reject(response, 401, "Token无效");
            } catch (Exception e) {
                log.error("WebSocket握手异常:", e);
                return reject(response, 401, "Token无效");
            }
        }

        // ========== 秘钥认证 ==========
        if (key != null && !key.isBlank()) {
            // 根据秘钥查询秘钥持有者(该连接只被授权查看此用户的数据)
            UserAuthEntity user = userAuthMapper.selectByKey(key);

            if (user == null || user.getUser_email() == null) {
                log.warn("WebSocket握手失败，秘钥无效或已作废");
                return reject(response, 401, "秘钥无效或已作废");
            }

            attributes.put(DeviceWebSocketHandler.ATTR_USER_ID, user.getUser_email());
            attributes.put(
                    DeviceWebSocketHandler.ATTR_AUTH_MODE,
                    DeviceWebSocketHandler.AUTH_MODE_KEY);

            return true;
        }

        // ========== 未提供认证信息 ==========
        log.warn("WebSocket握手失败，缺少token或key认证参数");
        return reject(response, 401, "缺少token或key认证参数");
    }

    /**
     * 解析URL原始查询串为参数Map，并对参数值进行URL解码
     * (秘钥可能包含 $ & % # 等特殊字符，因此必须正确处理编码)
     *
     * @param rawQuery 原始查询串(可能为null)
     * @return 参数Map
     */
    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();

        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }

        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int index = pair.indexOf('=');
            String name;
            String value;
            if (index < 0) {
                name = pair;
                value = "";
            } else {
                name = pair.substring(0, index);
                value = pair.substring(index + 1);
            }
            if (name.isEmpty()) {
                continue;
            }
            params.put(
                    decode(name),
                    decode(value)
            );
        }
        return params;
    }

    /**
     * URL解码
     */
    private String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("URL参数解码失败，使用原始值: {}", value);
            return value;
        }
    }

    /**
     * 拒绝握手并返回错误
     *
     * @return false(拒绝握手)
     */
    private boolean reject(ServerHttpResponse response, int code, String message) {
        try {
            response.setStatusCode(HttpStatus.valueOf(code));

            // 尝试写入错误JSON(部分非浏览器客户端可读取)
            if (response instanceof ServletServerHttpResponse servletResponse) {
                byte[] body = objectMapper.writeValueAsBytes(
                        new WsErrorMessage(code, message)
                );
                servletResponse.getServletResponse()
                        .setCharacterEncoding(StandardCharsets.UTF_8.name());
                servletResponse.getServletResponse()
                        .setContentType("application/json;charset=UTF-8");
                servletResponse.getBody().write(body);
                servletResponse.getBody().flush();
            }
        } catch (Exception e) {
            log.warn("写入握手错误响应失败: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 握手之后
     */
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {

    }
}
