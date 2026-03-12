package com.demo.avatar;

import com.demo.config.AvatarStorageProperties;
import com.demo.dto.user.AvatarUploadConfigRequest;
import com.demo.dto.user.AvatarUploadTicketRequest;
import com.demo.exception.BusinessException;
import com.demo.vo.AvatarUploadConfigVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 本地文件头像存储 provider。
 */
@Slf4j
@Component
public class LocalAvatarStorageProvider implements AvatarStorageProvider {

    private static final Map<String, String> CONTENT_TYPE_BY_SUFFIX = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png"
    );

    private final AvatarStorageProperties avatarStorageProperties;

    public LocalAvatarStorageProvider(AvatarStorageProperties avatarStorageProperties) {
        this.avatarStorageProperties = avatarStorageProperties;
    }

    @Override
    public String getName() {
        return "local-file-storage";
    }

    @Override
    public AvatarUploadConfigVO generateUploadConfig(Long userId, AvatarUploadConfigRequest request, String baseUrl) {
        String normalizedFileName = normalizeSourceFileName(request.getFileName());
        String suffix = validateFileNameAndResolveSuffix(normalizedFileName);
        String expectedContentType = expectedContentTypeBySuffix(suffix);
        validateRequestContentType(expectedContentType, request.getContentType());

        String fileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        String objectKey = buildObjectKey(fileName);
        long expiresAt = Instant.now().plusSeconds(avatarStorageProperties.getUploadConfigTtlSeconds()).getEpochSecond();
        String signature = signTicket(userId, objectKey, expiresAt);

        String uploadUrl = UriComponentsBuilder
                .fromUriString(joinBaseUrl(baseUrl, "/user/me/avatar/upload"))
                .queryParam("key", objectKey)
                .queryParam("expires", expiresAt)
                .queryParam("signature", signature)
                .build()
                .toUriString();
        String resourceUrl = joinBaseUrl(baseUrl, buildPublicResourcePath(objectKey));

        return AvatarUploadConfigVO.builder()
                .uploadUrl(uploadUrl)
                .resourceUrl(resourceUrl)
                .expiresIn(avatarStorageProperties.getUploadConfigTtlSeconds())
                .extraHeaders(Map.of("content-type", expectedContentType))
                .build();
    }

    @Override
    public String uploadAvatar(Long userId,
                               AvatarUploadTicketRequest request,
                               String contentType,
                               long contentLength,
                               InputStream inputStream,
                               String baseUrl) {
        String objectKey = normalizeAndValidateObjectKey(request.getKey());
        validateTicket(userId, objectKey, request.getExpires(), request.getSignature());

        String expectedContentType = expectedContentTypeByObjectKey(objectKey);
        validateUploadContentType(expectedContentType, contentType);
        validateContentLength(contentLength);

        byte[] bytes = readContent(inputStream);
        Path targetPath = resolveTargetPath(objectKey);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            log.error("store avatar failed: objectKey={}, err={}", objectKey, ex.getMessage(), ex);
            throw new BusinessException("头像上传失败，请稍后重试");
        }
        return joinBaseUrl(baseUrl, buildPublicResourcePath(objectKey));
    }

    @Override
    public boolean supportsAvatarUrl(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return false;
        }
        String normalized = avatarUrl.trim();
        String path = normalized;
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            try {
                path = UriComponentsBuilder.fromUriString(normalized).build().toUri().getPath();
            } catch (Exception ex) {
                return false;
            }
        }
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String normalizedPath = path.replace("\\", "/");
        String prefix = buildPublicAvatarDirPrefix();
        String lowerPath = normalizedPath.toLowerCase(Locale.ROOT);
        return normalizedPath.startsWith(prefix)
                && (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg") || lowerPath.endsWith(".png"));
    }

    private String normalizeSourceFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new BusinessException("文件名不能为空");
        }
        String normalized = fileName.trim();
        if (normalized.length() > 100) {
            throw new BusinessException("文件名长度不能超过100个字符");
        }
        if (normalized.contains("/") || normalized.contains("\\") || normalized.contains("..")) {
            throw new BusinessException("文件名不合法");
        }
        return normalized;
    }

    private String validateFileNameAndResolveSuffix(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String suffix : CONTENT_TYPE_BY_SUFFIX.keySet()) {
            if (lower.endsWith(suffix)) {
                return suffix;
            }
        }
        throw new BusinessException("文件名需以 .jpg/.jpeg/.png 结尾");
    }

    private void validateRequestContentType(String expectedContentType, String contentType) {
        String normalizedContentType = normalizeContentType(contentType);
        if (!expectedContentType.equals(normalizedContentType)) {
            if ("image/png".equals(expectedContentType)) {
                throw new BusinessException("PNG 头像需使用 image/png 上传");
            }
            throw new BusinessException("JPEG 头像需使用 image/jpeg 上传");
        }
    }

    private void validateUploadContentType(String expectedContentType, String contentType) {
        String normalizedContentType = normalizeContentType(contentType);
        if (!expectedContentType.equals(normalizedContentType)) {
            throw new BusinessException("头像 content-type 与上传配置不匹配");
        }
    }

    private String normalizeAndValidateObjectKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException("上传 key 不能为空");
        }
        String normalized = objectKey.trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("..")) {
            throw new BusinessException("上传 key 不合法");
        }
        String avatarDir = avatarStorageProperties.normalizedAvatarDir();
        String prefix = avatarDir + "/";
        if (!normalized.startsWith(prefix)) {
            throw new BusinessException("上传 key 不合法");
        }
        String fileName = normalized.substring(prefix.length());
        if (!fileName.matches("[a-f0-9]{32}\\.(jpg|jpeg|png)")) {
            throw new BusinessException("上传 key 不合法");
        }
        return normalized;
    }

    private void validateTicket(Long userId, String objectKey, Long expires, String signature) {
        if (expires == null || expires <= 0) {
            throw new BusinessException("上传票据已失效，请重新获取上传配置");
        }
        long now = Instant.now().getEpochSecond();
        if (expires < now) {
            throw new BusinessException("上传地址已过期，请重新获取上传配置");
        }
        String expectedSignature = signTicket(userId, objectKey, expires);
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = (signature == null ? "" : signature).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new BusinessException("上传签名无效，请重新获取上传配置");
        }
    }

    private void validateContentLength(long contentLength) {
        if (contentLength == 0L) {
            throw new BusinessException("上传文件不能为空");
        }
        if (contentLength > avatarStorageProperties.getMaxFileSizeBytes()) {
            throw new BusinessException(buildMaxSizeExceededMessage());
        }
    }

    private byte[] readContent(InputStream inputStream) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long totalBytes = 0L;
            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1) {
                totalBytes += readBytes;
                if (totalBytes > avatarStorageProperties.getMaxFileSizeBytes()) {
                    throw new BusinessException(buildMaxSizeExceededMessage());
                }
                outputStream.write(buffer, 0, readBytes);
            }
            if (totalBytes == 0L) {
                throw new BusinessException("上传文件不能为空");
            }
            return outputStream.toByteArray();
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("read avatar content failed: {}", ex.getMessage(), ex);
            throw new BusinessException("头像上传失败，请稍后重试");
        }
    }

    private Path resolveTargetPath(String objectKey) {
        Path rootPath = avatarStorageProperties.resolveLocalRootPath();
        Path targetPath = rootPath.resolve(objectKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new BusinessException("上传路径不合法");
        }
        return targetPath;
    }

    private String expectedContentTypeByObjectKey(String objectKey) {
        int lastDot = objectKey.lastIndexOf('.');
        if (lastDot < 0) {
            throw new BusinessException("上传 key 不合法");
        }
        return expectedContentTypeBySuffix(objectKey.substring(lastDot).toLowerCase(Locale.ROOT));
    }

    private String expectedContentTypeBySuffix(String suffix) {
        String contentType = CONTENT_TYPE_BY_SUFFIX.get(suffix.toLowerCase(Locale.ROOT));
        if (contentType == null) {
            throw new BusinessException("仅支持 JPG/JPEG/PNG 头像上传");
        }
        return contentType;
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int separatorIndex = normalized.indexOf(';');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(0, separatorIndex).trim();
        }
        return normalized;
    }

    private String buildObjectKey(String fileName) {
        return avatarStorageProperties.normalizedAvatarDir() + "/" + fileName;
    }

    private String buildPublicResourcePath(String objectKey) {
        return avatarStorageProperties.normalizedPublicUrlPrefix() + "/" + objectKey;
    }

    private String buildPublicAvatarDirPrefix() {
        return avatarStorageProperties.normalizedPublicUrlPrefix() + "/" + avatarStorageProperties.normalizedAvatarDir() + "/";
    }

    private String joinBaseUrl(String baseUrl, String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        if (!StringUtils.hasText(baseUrl)) {
            return normalizedPath;
        }
        String normalizedBaseUrl = baseUrl.trim();
        while (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + normalizedPath;
    }

    private String signTicket(Long userId, String objectKey, Long expires) {
        try {
            String payload = userId + "\n" + objectKey + "\n" + expires;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    avatarStorageProperties.getUploadSignSecret().getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
            ));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            log.error("sign avatar ticket failed: {}", ex.getMessage(), ex);
            throw new IllegalStateException("avatar ticket sign failed", ex);
        }
    }

    private String buildMaxSizeExceededMessage() {
        long maxSizeKb = Math.max(1L, avatarStorageProperties.getMaxFileSizeBytes() / 1024L);
        return "头像文件不能超过" + maxSizeKb + "KB";
    }
}
