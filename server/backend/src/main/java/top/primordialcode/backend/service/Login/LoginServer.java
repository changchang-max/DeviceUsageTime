package top.primordialcode.backend.service.Login;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import top.primordialcode.backend.dto.LoginDTO;
import top.primordialcode.backend.entity.UserAuthEntity;
import top.primordialcode.backend.mapper.UserAuthMapper;
import top.primordialcode.backend.service.Login.impl.LoginServerImpl;
import top.primordialcode.backend.utils.JwtUtil;

@Slf4j
@Service
public class LoginServer implements LoginServerImpl {
    @Autowired
    UserAuthMapper userAuthMapper;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtUtil jwtUtil;

    /**
     * 用户登录，检验密码是否正确，检验成功则返回token记住用户
     * @param loginDTO 用户登录时需要传入的数据，包含用户名和密码
     * @return 返回一个jwt的token，类型为String
     */
    @Override
    public String login(LoginDTO loginDTO) {
        //根据用户邮箱拿到用户实体类
        UserAuthEntity user = userAuthMapper.selectUserInfo(loginDTO.getUser_email());
        if(user == null){
            throw new RuntimeException("用户不存在");
        }

        if(!passwordEncoder.matches(loginDTO.getUser_password(), user.getUser_password())){
            //记录日志
            log.info(loginDTO.getUser_email()+"输入密码错误");
            throw new RuntimeException("密码错误");
        }

        // 密码正确时，允许登录，给浏览器返回一个tokken
        return jwtUtil.generate(loginDTO.getUser_email());
    }

    /**
     * 根据用户token删除对应信息以此实现退出登录
     * @param token 用户token
     */
    @Override
    public void logout(String token) {
        //检查用户token是否存在。若存在则删除。若存在，则删除，并记录日志。若不存在，则记录日志。
        return;
    }
}
