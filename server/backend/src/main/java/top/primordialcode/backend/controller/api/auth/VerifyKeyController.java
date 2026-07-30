package top.primordialcode.backend.controller.api.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.service.VerifyKey.VerifyKeyService;
import top.primordialcode.backend.vo.VerifyKeyVO;

/**
 * 秘钥验证控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class VerifyKeyController {

    @Autowired
    private VerifyKeyService verifyKeyService;

    /**
     * 验证秘钥接口
     * @param key 要验证的秘钥
     * @return 返回验证结果和VO数据
     */
    @GetMapping("/verify-key")
    public Result verifyKey(@RequestParam("key") String key) {
        try {
            // 调用Service层处理业务逻辑
            VerifyKeyVO vo = verifyKeyService.verifyKeyAndGenerateToken(key);
            return Result.success("秘钥有效", vo);
            
        } catch (RuntimeException e) {
            // Service层抛出的业务异常
            log.error("秘钥验证失败: {}", e.getMessage());
            return Result.error(401, e.getMessage(), null);
        } catch (Exception e) {
            // 其他未预期的异常
            log.error("秘钥验证过程发生异常", e);
            return Result.error(500, "服务器内部错误", null);
        }
    }
}
