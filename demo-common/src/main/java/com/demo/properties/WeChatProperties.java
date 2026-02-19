package com.demo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信支付与小程序配置项。
 */
@Component
@ConfigurationProperties(prefix = "sky.wechat")
@Data
public class WeChatProperties {

    /** 小程序 AppID。 */
    private String appid;
    /** 小程序 AppSecret。 */
    private String secret;
    /** 商户号。 */
    private String mchid;
    /** 商户 API 证书序列号。 */
    private String mchSerialNo;
    /** 商户私钥文件路径。 */
    private String privateKeyFilePath;
    /** API v3 密钥。 */
    private String apiV3Key;
    /** 微信平台证书文件路径。 */
    private String weChatPayCertFilePath;
    /** 支付成功回调地址。 */
    private String notifyUrl;
    /** 退款成功回调地址。 */
    private String refundNotifyUrl;
}
