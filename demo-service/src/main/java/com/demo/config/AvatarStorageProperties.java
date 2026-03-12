package com.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 头像存储配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "avatar-storage")
public class AvatarStorageProperties {

    /**
     * 当前启用的头像存储 provider。
     */
    private String provider = "local";

    /**
     * 上传配置有效期，单位秒。
     */
    private int uploadConfigTtlSeconds = 300;

    /**
     * 头像最大大小，单位字节。
     */
    private long maxFileSizeBytes = 2 * 1024 * 1024;

    /**
     * 对外暴露的静态资源前缀。
     */
    private String publicUrlPrefix = "/uploads";

    /**
     * 上传签名密钥。
     */
    private String uploadSignSecret = "day20-avatar-local-sign";

    /**
     * 本地 provider 配置。
     */
    private Local local = new Local();

    public Path resolveLocalRootPath() {
        return Paths.get(local.getRootDir()).toAbsolutePath().normalize();
    }

    public String normalizedPublicUrlPrefix() {
        String prefix = StringUtils.hasText(publicUrlPrefix) ? publicUrlPrefix.trim() : "/uploads";
        prefix = prefix.replace("\\", "/");
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        while (prefix.endsWith("/") && prefix.length() > 1) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    public String normalizedAvatarDir() {
        String avatarDir = StringUtils.hasText(local.getAvatarDir()) ? local.getAvatarDir().trim() : "avatars";
        avatarDir = avatarDir.replace("\\", "/");
        while (avatarDir.startsWith("/")) {
            avatarDir = avatarDir.substring(1);
        }
        while (avatarDir.endsWith("/")) {
            avatarDir = avatarDir.substring(0, avatarDir.length() - 1);
        }
        return avatarDir.isEmpty() ? "avatars" : avatarDir;
    }

    @Data
    public static class Local {
        /**
         * 本地上传根目录。
         */
        private String rootDir = "uploads";

        /**
         * 头像子目录。
         */
        private String avatarDir = "avatars";
    }
}
