package com.demo.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Fails closed when a production process is started without real secrets. */
@Component
@Profile("prod")
public class ProductionSecretsValidator implements InitializingBean {

    private static final List<String> REQUIRED = List.of(
            "SECONDHAND_MYSQL_PASSWORD",
            "SECONDHAND_REDIS_PASSWORD",
            "SECONDHAND_MONGO_ROOT_PASSWORD",
            "SECONDHAND_RABBITMQ_PASSWORD",
            "SECONDHAND_JWT_ADMIN_SECRET",
            "SECONDHAND_JWT_USER_SECRET",
            "SECONDHAND_UPLOAD_SIGN_SECRET",
            "SECONDHAND_PAYMENT_MOCK_SIGN"
    );

    private static final Set<String> DISALLOWED = Set.of(
            "dev-admin-secret-change-me",
            "dev-user-secret-change-me",
            "secondhand_dev_2026",
            "secondhand_mq_2026",
            "day20-dev-avatar-sign"
    );

    @Override
    public void afterPropertiesSet() {
        for (String name : REQUIRED) {
            String value = System.getenv(name);
            if (value == null || value.isBlank() || DISALLOWED.contains(value)) {
                throw new IllegalStateException("Production secret is missing or unsafe: " + name);
            }
        }
    }
}
