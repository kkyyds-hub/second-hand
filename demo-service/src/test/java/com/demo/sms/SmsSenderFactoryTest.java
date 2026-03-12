package com.demo.sms;

import com.demo.config.SmsProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 短信 sender 选择回归测试。
 */
public class SmsSenderFactoryTest {

    @Test
    void shouldUseMockSenderByDefault() {
        SmsProperties properties = new SmsProperties();
        MockSmsSender mockSmsSender = new MockSmsSender(properties);
        SmsSenderFactory factory = new SmsSenderFactory(properties, mockSmsSender);

        Assertions.assertSame(mockSmsSender, factory.getSender());
    }

    @Test
    void shouldFallbackToMockSenderWhenProviderUnknown() {
        SmsProperties properties = new SmsProperties();
        properties.setProvider("aliyun");
        MockSmsSender mockSmsSender = new MockSmsSender(properties);
        SmsSenderFactory factory = new SmsSenderFactory(properties, mockSmsSender);

        Assertions.assertSame(mockSmsSender, factory.getSender());
    }
}
