package com.demo.avatar;

import com.demo.config.AvatarStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Locale;

/**
 * 头像存储 provider 工厂。
 */
@Slf4j
@Component
public class AvatarStorageProviderFactory {

    private final AvatarStorageProperties avatarStorageProperties;
    private final LocalAvatarStorageProvider localAvatarStorageProvider;

    public AvatarStorageProviderFactory(AvatarStorageProperties avatarStorageProperties,
                                        LocalAvatarStorageProvider localAvatarStorageProvider) {
        this.avatarStorageProperties = avatarStorageProperties;
        this.localAvatarStorageProvider = localAvatarStorageProvider;
    }

    @PostConstruct
    public void logProvider() {
        log.info("avatar storage provider loaded: {}", getProvider().getName());
    }

    public AvatarStorageProvider getProvider() {
        String provider = avatarStorageProperties.getProvider();
        if (provider == null) {
            return localAvatarStorageProvider;
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "local-file-storage":
            case "local_file_storage":
            case "localfilestorage":
            case "local":
            default:
                if (!"local".equals(normalized)
                        && !"local-file-storage".equals(normalized)
                        && !"local_file_storage".equals(normalized)
                        && !"localfilestorage".equals(normalized)) {
                    log.warn("unknown avatar storage provider: {}, fallback to local", provider);
                }
                return localAvatarStorageProvider;
        }
    }
}
