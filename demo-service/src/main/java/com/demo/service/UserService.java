package com.demo.service;

import com.demo.dto.user.*;
import com.demo.result.PageResult;
import com.demo.vo.AvatarUploadConfigVO;
import com.demo.vo.UserVO;

import javax.validation.Valid;
import java.util.List;

/**
 * UserService 接口。
 */
public interface UserService {
    PageResult<UserVO> getUserPage(UserQueryDTO queryDTO);


    UserVO updateProfile(UpdateProfileRequest request);

    AvatarUploadConfigVO generateAvatarUploadConfig(@Valid AvatarUploadConfigRequest request);

    void changePassword(ChangePasswordRequest request);

    UserVO bindPhone(BindPhoneRequest request);

    UserVO bindEmail(BindEmailRequest request);

    void unbindPhone(@Valid UnbindContactRequest request);

    void unbindEmail(@Valid UnbindContactRequest request);

    void requireSeller(Long userId);

    /**
     * Day13 Step7 - 封禁用户
     */
    String banUser(Long userId);

    /**
     * Day13 Step7 - 解封用户
     */
    String unbanUser(Long userId);

    /**
     * Day13 Step7 - 导出用户 CSV
     */
    String exportUsersCSV(String keyword, java.time.LocalDateTime startTime, java.time.LocalDateTime endTime);
}
