package com.demo.avatar;

import com.demo.dto.user.AvatarUploadConfigRequest;
import com.demo.dto.user.AvatarUploadTicketRequest;
import com.demo.vo.AvatarUploadConfigVO;

import java.io.InputStream;

/**
 * 头像存储 provider 抽象。
 */
public interface AvatarStorageProvider {

    /**
     * @return provider 名称。
     */
    String getName();

    /**
     * 生成头像上传配置。
     */
    AvatarUploadConfigVO generateUploadConfig(Long userId, AvatarUploadConfigRequest request, String baseUrl);

    /**
     * 执行头像上传。
     */
    String uploadAvatar(Long userId,
                        AvatarUploadTicketRequest request,
                        String contentType,
                        long contentLength,
                        InputStream inputStream,
                        String baseUrl);

    /**
     * 判断头像 URL 是否属于当前 provider 的合法资源。
     */
    boolean supportsAvatarUrl(String avatarUrl);
}
