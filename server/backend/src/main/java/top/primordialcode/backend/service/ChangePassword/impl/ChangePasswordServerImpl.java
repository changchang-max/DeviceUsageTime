package top.primordialcode.backend.service.ChangePassword.impl;

import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.ChangePasswordDTO;

/**
 * 修改密码服务接口
 */
public interface ChangePasswordServerImpl {

    /**
     * 修改账号密码。已登录时以Token解析出的身份为准(user_email字段被忽略)，
     * 未登录时需要校验user_email、原密码与新密码格式。
     * 修改成功后当前Token立即失效，用户需重新登录。
     *
     * @param token Authorization请求头值(形如 Bearer xxx)，未登录时可为null
     * @param changePasswordDTO 修改密码请求参数(含user_email/old_password/new_password)
     * @return 操作结果。成功返回"密码修改成功"；原密码错误/格式不合规/用户不存在等返回400
     */
    Result changePassword(String token, ChangePasswordDTO changePasswordDTO);
}
