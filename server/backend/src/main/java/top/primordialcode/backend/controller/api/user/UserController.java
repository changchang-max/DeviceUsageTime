package top.primordialcode.backend.controller.api.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.service.User.UserService;
import top.primordialcode.backend.vo.user.RegenerateKeyVO;
import top.primordialcode.backend.vo.user.UserProfileVO;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    /**
     * 获取当前登录用户的个人信息
     * @return 用户信息（不包含密码）
     */
    @GetMapping("/profile")
    public Result getUserProfile() {
        // 从SecurityContext获取当前认证用户的邮箱
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = (String) authentication.getPrincipal();
        
        log.info("用户请求获取个人信息，邮箱：{}", email);
        
        try {
            // 调用服务层获取用户信息
            UserAuthEntity user = userService.getUserProfile(email);
            
            // 转换为VO对象
            UserProfileVO profileVO = new UserProfileVO();
            profileVO.setEmail(user.getUser_email());
            profileVO.setSecretKey(user.getUser_key());
            profileVO.setCreatedAt(user.getCreated_at());
            profileVO.setLastLoginAt(user.getLast_login_at());
            
            return Result.success("获取用户信息成功", profileVO);
        } catch (RuntimeException e) {
            log.error("获取用户信息失败：{}", e.getMessage());
            return Result.error(404, "用户不存在", null);
        }
    }

    /**
     * 重新生成用户秘钥
     * @return 新的秘钥
     */
    @PostMapping("/regenerate-key")
    public Result regenerateKey() {
        // 从SecurityContext获取当前认证用户的邮箱
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = (String) authentication.getPrincipal();
        
        log.info("用户请求重新生成秘钥，邮箱：{}", email);
        
        try {
            // 调用服务层重新生成秘钥
            String newKey = userService.regenerateSecretKey(email);
            
            // 转换为VO对象
            RegenerateKeyVO regenerateKeyVO = new RegenerateKeyVO();
            regenerateKeyVO.setSecretKey(newKey);
            
            return Result.success("秘钥已重新生成", regenerateKeyVO);
        } catch (RuntimeException e) {
            log.error("重新生成秘钥失败：{}", e.getMessage());
            return Result.error(500, "重新生成秘钥失败", null);
        }
    }
}
