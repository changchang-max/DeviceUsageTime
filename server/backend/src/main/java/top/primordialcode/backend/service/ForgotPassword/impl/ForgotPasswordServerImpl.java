package top.primordialcode.backend.service.ForgotPassword.impl;

import top.primordialcode.backend.common.Result;
import top.primordialcode.backend.dto.ForgotPasswordDTO;
import top.primordialcode.backend.dto.SendResetCodeDTO;
import top.primordialcode.backend.vo.forgot.ForgotPasswordCodeVO;

/**
 * 忘记密码服务接口
 * 1. 发送重置验证码：生成6位验证码写入Redis(3分钟有效)并发送邮件
 * 2. 校验验证码并重置密码：新密码落库后立即吊销该账号的全部登录凭证
 */
public interface ForgotPasswordServerImpl {

    /**
     * 发送重置密码验证码
     * 验证码写入Redis的 forgot:password:code:[user_email]，存活时间3分钟，
     * 同一邮箱60秒内只允许重复获取一次。
     *
     * @param sendResetCodeDTO 请求参数(user_email必填)
     * @return 操作结果。成功时 data 为 ForgotPasswordCodeVO(含邮箱与验证码有效期秒数)
     */
    Result sendResetCode(SendResetCodeDTO sendResetCodeDTO);

    /**
     * 校验验证码并重置密码
     * 验证码校验通过后被立即删除(一次有效)；新密码写入成功后会将该账号下
     * 所有已签发的Token加入Redis黑名单(按签发时间戳 epoch:jwt:blacklist:签发时间)
     * 并作废上传凭证，使该账号所有凭证立即失效。
     *
     * @param forgotPasswordDTO 请求参数(user_email/new_password/code必填)
     * @return 操作结果。成功返回"密码重置成功，请重新登录"；验证码错误/过期、密码格式不合规等返回400
     */
    Result resetPassword(ForgotPasswordDTO forgotPasswordDTO);

    /**
     * 验证码有效期(秒)，供前端倒计时展示
     *
     * @return 有效期秒数
     */
    long getVerifyCodeTtlSeconds();
}