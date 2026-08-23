package top.primordialcode.backend.service.User;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;

@Slf4j
@Service
public class UserService {
    @Autowired
    private UserAuthMapper userAuthMapper;

    /**
     * 根据用户邮箱获取用户档案信息
     * @param email 用户邮箱
     * @return 用户实体类，不包含密码
     */
    public UserAuthEntity getUserProfile(String email) {
        log.info("获取用户档案，邮箱：{}", email);
        
        UserAuthEntity user = userAuthMapper.selectProfileByEmail(email);
        
        if (user == null) {
            log.warn("用户不存在，邮箱：{}", email);
            throw new RuntimeException("用户不存在");
        }
        
        return user;
    }
}
