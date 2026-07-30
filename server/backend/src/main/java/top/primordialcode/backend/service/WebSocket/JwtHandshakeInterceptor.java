package top.primordialcode.backend.service.WebSocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import top.primordialcode.backend.utils.JwtUtil;

import java.net.URI;
import java.util.Map;

/*Jwt握手拦截器*/
@Slf4j
@Component
public class JwtHandshakeInterceptor
        implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    public JwtHandshakeInterceptor(JwtUtil jwtUtil){
        this.jwtUtil = jwtUtil;
    }

    //握手之前
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String,Object> attributes) {


        // 获取URL参数
        URI uri = request.getURI();

        String query = uri.getQuery();

        if(query == null){
            return false;
        }

        // token=xxxx
        String token =
                query.substring(
                        query.indexOf("=")+1
                );

        try {
            //解析JWT
            String email = jwtUtil.parse(token);

            //保存用户信息
            attributes.put("email", email);

            return true;
        }catch(Exception e){
            log.warn("token可能已过期");
            log.error("JwtHandshakeInterceptor:",e);
            return false;
        }
    }


    //握手之后，因为没什么需要处理的，所以没有填充具体代码
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception){

    }

}