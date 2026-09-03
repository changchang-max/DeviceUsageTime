package top.primordialcode.backend.vo.websocket;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket 错误消息(服务端 → 客户端)
 *
 * 消息格式:
 * {
 *   "type": "error",
 *   "code": 401,
 *   "message": "Token无效或已过期"
 * }
 *
 * 常见错误码:
 * 400: 消息格式错误
 * 401: Token或秘钥无效
 * 403: 无权限订阅该用户
 * 404: 用户不存在
 */
@Data
@NoArgsConstructor
public class WsErrorMessage {

    /**
     * 消息类型
     */
    private String type = "error";

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误描述
     */
    private String message;

    public WsErrorMessage(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
