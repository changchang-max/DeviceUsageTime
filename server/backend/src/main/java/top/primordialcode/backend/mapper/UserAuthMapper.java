package top.primordialcode.backend.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import top.primordialcode.backend.entity.UserAuthEntity;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

@Mapper
public interface UserAuthMapper {

    /**
     * 返回所有用户的邮箱和key（注：不要含密码）
     * @return 返回UserAuthEntity对象，其中user_password应为null
     */
    UserAuthEntity findAllUserEmail();

    /**
     * 增加新的用户信息
     * @param user 完整的entity对象
     * @return 受影响的行数
     */
    int insert(UserAuthEntity user);

    /**
     * 删除用户信息
     * @param email 用户邮箱
     * @return 受影响的行数
     */
    int deleteByEmail(String email);

    /**
     * 更新用户信息
     * @param user 应传入完整的UserAuth
     * @return 受影响的行数
     */
    int update(UserAuthEntity user);

    /**
     * 仅更新user_key
     * @param email 要修改的目标用户邮箱
     * @param user_key 新的user_key
     * @return 受影响的行数
     */
    int updateKey(@Param("email") String email, @Param("user_key") String user_key);

    /**
     * 查询用户是否存在
     * @param user_email 要查询的目标邮箱
     * @return true：存在 flase：不存在
     */
    boolean existsByEmail(String user_email);

    /**
     * 根据用户邮箱查询密码
     * @param user_email 要查询的目标邮箱
     * @return 用户实体类，其中仅有user_email与user_password属性可用
     */
    UserAuthEntity selectUserInfo(String user_email);

    /**
     * 根据用户秘钥查询用户信息
     * @param user_key 要查询的秘钥
     * @return 用户实体类,包含user_email和user_name
     */
    UserAuthEntity selectByKey(String user_key);

    /**
     * 更新用户的最后登录时间
     * @param email 要更新的目标用户邮箱
     * @param last_login_at 最后登录时间
     * @return 受影响的行数
     */
    int updateLastLoginAt(@Param("email") String email, @Param("last_login_at") Instant last_login_at);

    /**
     * 更新用户的登录密码
     * @param email 要修改密码的目标用户邮箱
     * @param user_password 加密后的新密码
     * @return 受影响的行数
     */
    int updatePassword(@Param("email") String email, @Param("user_password") String user_password);

    /**
     * 根据用户邮箱查询用户档案信息(不包含密码)
     * @param user_email 要查询的目标邮箱
     * @return 用户实体类，包含除密码外的所有信息
     */
    UserAuthEntity selectProfileByEmail(String user_email);

    /**
     * 作废用户秘钥(将user_key设置为null)
     * @param email 要作废秘钥的目标用户邮箱
     * @return 受影响的行数
     */
    int revokeKey(@Param("email") String email);
}
