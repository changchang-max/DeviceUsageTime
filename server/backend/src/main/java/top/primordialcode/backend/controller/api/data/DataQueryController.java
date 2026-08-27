package top.primordialcode.backend.controller.api.data;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.exception.UserNotFoundException;
import top.primordialcode.backend.service.Data.DataQueryServer;
import top.primordialcode.backend.vo.data.RealtimeDataVO;

@Slf4j
@RestController
@RequestMapping("/data")
public class DataQueryController {

    @Autowired
    DataQueryServer dataQueryServer;

    /**
     * 获取用户当前实时数据
     * 支持Token认证(Authorization请求头)或秘钥认证(key请求参数)
     * @param authorization Bearer token，可选
     * @param key 用户秘钥，可选
     * @return 实时数据
     */
    @GetMapping("/realtime")
    public Result getRealtime(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "key", required = false) String key) {
        try {
            RealtimeDataVO vo = dataQueryServer.getRealtimeData(authorization, key);
            return Result.success("获取实时数据成功", vo);
        } catch (SecurityException e) {
            log.warn("获取实时数据认证失败: {}", e.getMessage());
            return Result.error(401, "Token或秘钥无效", null);
        } catch (UserNotFoundException e) {
            log.warn("获取实时数据失败: {}", e.getMessage());
            return Result.error(404, "用户不存在", null);
        }
    }
}
