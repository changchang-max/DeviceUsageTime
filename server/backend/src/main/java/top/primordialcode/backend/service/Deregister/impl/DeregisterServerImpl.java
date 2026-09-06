package top.primordialcode.backend.service.Deregister.impl;

import top.primordialcode.backend.common.Result;

/**
 * 注销账号服务接口
 */
public interface DeregisterServerImpl {

    /**
     * 注销当前账号：二次校验登录密码后，永久删除用户记录及全部关联数据，并使当前Token立即失效
     * @param token 请求头中的 Authorization 值(形如 Bearer xxx)
     * @param user_password 用户输入的登录密码(二次确认)
     * @return 操作结果
     */
    Result deregister(String token, String user_password);
}
