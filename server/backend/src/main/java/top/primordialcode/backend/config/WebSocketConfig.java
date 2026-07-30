package top.primordialcode.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import top.primordialcode.backend.service.WebSocket.DeviceWebSocketHandler;
import top.primordialcode.backend.service.WebSocket.JwtHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final DeviceWebSocketHandler handler;
    private final JwtHandshakeInterceptor interceptor;

    public WebSocketConfig(
            DeviceWebSocketHandler handler,
            JwtHandshakeInterceptor interceptor){

        this.handler = handler;
        this.interceptor = interceptor;

    }


    /**
     * 向 Spring WebSocket 注册一个连接入口 /ws/device，并且在建立连接前执行 JWT 拦截认证。
     * @param registry SpringBoot提供的WebSocket注册中心，一个保存「URL路径 → WebSocket处理器」映射关系的容器。
     */
    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry){


        registry.addHandler(
                        //handler负责接收客户端消息，发送消息。管理session
                        handler,
                        //这是 WebSocket 访问地址。
                        "/ws/device"
                )
                // 添加认证。WebSocket正式建立之前，先执行认证。如果不加这个拦截器，则任何人都可以连接
                .addInterceptors(
                        interceptor
                )
                //跨域配置。*表示允许所有来源。todo 在生产环境下应该设为自己的域名。
                .setAllowedOrigins("*");

    }

}