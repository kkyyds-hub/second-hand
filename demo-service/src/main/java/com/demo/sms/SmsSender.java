package com.demo.sms;

/**
 * 短信验证码发送抽象。
 *
 * 职责：
 * 1) 统一“发送动作”的扩展点；
 * 2) 让 AuthServiceImpl 只关心验证码生成、缓存和校验；
 * 3) 便于 mock 与真实短信网关平滑切换。
 */
public interface SmsSender {

    /**
     * @return sender 名称，用于日志与诊断。
     */
    String getName();

    /**
     * 发送验证码。
     *
     * @param mobile 目标手机号
     * @param code 验证码
     * @param ttlMinutes 验证码有效期（分钟）
     */
    void sendCode(String mobile, String code, long ttlMinutes);

    /**
     * @return 当前 sender 对应的用户可见提示文案。
     */
    String buildSuccessMessage(long ttlMinutes);
}
