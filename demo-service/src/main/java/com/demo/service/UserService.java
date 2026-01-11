package com.demo.service;

import com.demo.dto.user.*;
import com.demo.result.PageResult;
import com.demo.vo.AvatarUploadConfigVO;
import com.demo.vo.UserVO;

import javax.validation.Valid;
import java.util.List;

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

}
