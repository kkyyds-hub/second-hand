package com.demo.mail;

/**
 * 邮件发送抽象。
 */
public interface EmailSender {

    /**
     * @return sender 名称
     */
    String getName();

    /**
     * 发送激活邮件。
     */
    void sendActivationMail(ActivationMail activationMail);
}
