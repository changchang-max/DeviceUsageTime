package top.primordialcode.backend.service.VerifyKey;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.VerifyKey.impl.VerifyKeyServiceImpl;
import top.primordialcode.backend.utils.JwtUtil;
import top.primordialcode.backend.vo.VerifyKeyVO;

/**
 * 秘钥验证服务实现类
 */
@Slf4j
@Service
public class VerifyKeyService implements VerifyKeyServiceImpl {
    
    @Autowired
    private UserAuthMapper userAuthMapper;
    
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 验证秘钥并返回VO对象
     * @param secretKey 要验证的秘钥
     * @return VerifyKeyVO对象,包含用户昵称和ROLE_VISITOR角色的token
     */
    @Override
    public VerifyKeyVO verifyKeyAndGenerateToken(String secretKey) {
        log.info("验证秘钥: {}", secretKey);
        
        // 根据秘钥查询用户信息
        UserAuthEntity user = userAuthMapper.selectByKey(secretKey);
        
        if (user == null) {
            log.warn("秘钥无效: {}", secretKey);
            throw new RuntimeException("秘钥无效或已作废");
        }
        
        log.info("秘钥验证成功，用户邮箱: {}, 用户昵称: {}", user.getUser_email(), user.getUser_name());
        
        // 生成带有ROLE_VISITOR角色的token
        String token = jwtUtil.generateWithRole(user.getUser_email(), "ROLE_VISITOR");
        
        // 封装返回数据为VO对象
        VerifyKeyVO vo = new VerifyKeyVO();
        vo.setUserName(user.getUser_name());
        vo.setToken(token);
        
        return vo;
    }
}
