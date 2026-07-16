package top.primordialcode.backend.service.Register.impl;

public interface VerifyCodeServerImpl {
    //生成并存储验证码到Redis，同时设置TTL存活时间
    public String savecode(String email);

    //查询验证码
    public String selectcode(String email);

    //删除验证码 返回结果成功或失败
    public boolean deletecode(String email);

    //查询指定用户是否存在
    public boolean existsByEmail(String email);
}
