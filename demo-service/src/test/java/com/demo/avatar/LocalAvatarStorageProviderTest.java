package com.demo.avatar;

import com.demo.config.AvatarStorageProperties;
import com.demo.dto.user.AvatarUploadConfigRequest;
import com.demo.dto.user.AvatarUploadTicketRequest;
import com.demo.exception.BusinessException;
import com.demo.vo.AvatarUploadConfigVO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

class LocalAvatarStorageProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldGenerateLocalUploadConfig() {
        LocalAvatarStorageProvider provider = new LocalAvatarStorageProvider(buildProperties());
        AvatarUploadConfigRequest request = new AvatarUploadConfigRequest();
        request.setFileName("avatar.png");
        request.setContentType("image/png");

        AvatarUploadConfigVO result = provider.generateUploadConfig(1001L, request, "http://localhost:8080");

        Assertions.assertTrue(result.getUploadUrl().startsWith("http://localhost:8080/user/me/avatar/upload"));
        Assertions.assertTrue(result.getResourceUrl().startsWith("http://localhost:8080/uploads/avatars/"));
        Assertions.assertEquals("image/png", result.getExtraHeaders().get("content-type"));
        Assertions.assertEquals(300, result.getExpiresIn());
    }

    @Test
    void shouldStoreAvatarWhenTicketValid() throws Exception {
        AvatarStorageProperties properties = buildProperties();
        LocalAvatarStorageProvider provider = new LocalAvatarStorageProvider(properties);
        AvatarUploadConfigVO config = provider.generateUploadConfig(1001L, pngRequest(), "http://localhost:8080");
        AvatarUploadTicketRequest ticketRequest = parseTicket(config.getUploadUrl());

        String resourceUrl = provider.uploadAvatar(
                1001L,
                ticketRequest,
                "image/png",
                4L,
                new ByteArrayInputStream("demo".getBytes(StandardCharsets.UTF_8)),
                "http://localhost:8080"
        );

        Path storedFile = properties.resolveLocalRootPath().resolve(ticketRequest.getKey());
        Assertions.assertEquals(config.getResourceUrl(), resourceUrl);
        Assertions.assertTrue(Files.exists(storedFile));
        Assertions.assertArrayEquals("demo".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(storedFile));
    }

    @Test
    void shouldRejectUploadWhenContentTypeMismatch() {
        LocalAvatarStorageProvider provider = new LocalAvatarStorageProvider(buildProperties());
        AvatarUploadConfigVO config = provider.generateUploadConfig(1001L, pngRequest(), "http://localhost:8080");
        AvatarUploadTicketRequest ticketRequest = parseTicket(config.getUploadUrl());

        BusinessException exception = Assertions.assertThrows(BusinessException.class, () ->
                provider.uploadAvatar(
                        1001L,
                        ticketRequest,
                        "image/jpeg",
                        4L,
                        new ByteArrayInputStream("demo".getBytes(StandardCharsets.UTF_8)),
                        "http://localhost:8080"
                )
        );

        Assertions.assertTrue(exception.getMessage().contains("content-type"));
    }

    private AvatarStorageProperties buildProperties() {
        AvatarStorageProperties properties = new AvatarStorageProperties();
        properties.setProvider("local-file-storage");
        properties.setUploadConfigTtlSeconds(300);
        properties.setMaxFileSizeBytes(1024L);
        properties.setPublicUrlPrefix("/uploads");
        properties.setUploadSignSecret("unit-test-secret");
        properties.getLocal().setRootDir(tempDir.resolve("uploads").toString());
        properties.getLocal().setAvatarDir("avatars");
        return properties;
    }

    private AvatarUploadConfigRequest pngRequest() {
        AvatarUploadConfigRequest request = new AvatarUploadConfigRequest();
        request.setFileName("avatar.png");
        request.setContentType("image/png");
        return request;
    }

    private AvatarUploadTicketRequest parseTicket(String uploadUrl) {
        MultiValueMap<String, String> queryParams = UriComponentsBuilder.fromUriString(uploadUrl).build().getQueryParams();
        AvatarUploadTicketRequest request = new AvatarUploadTicketRequest();
        request.setKey(queryParams.getFirst("key"));
        request.setExpires(Long.valueOf(queryParams.getFirst("expires")));
        request.setSignature(queryParams.getFirst("signature"));
        return request;
    }
}
