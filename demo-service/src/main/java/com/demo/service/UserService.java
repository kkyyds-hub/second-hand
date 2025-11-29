package com.demo.service;

import com.demo.dto.user.AvatarUploadConfigRequest;
import com.demo.dto.user.UpdateProfileRequest;
import com.demo.dto.user.UserQueryDTO;
import com.demo.result.PageResult;
import com.demo.vo.AvatarUploadConfigVO;
import com.demo.vo.UserVO;

import javax.validation.Valid;
import java.util.List;

public interface UserService {
    PageResult<UserVO> getUserPage(UserQueryDTO queryDTO);


    UserVO updateProfile(UpdateProfileRequest request);

    AvatarUploadConfigVO generateAvatarUploadConfig(@Valid AvatarUploadConfigRequest request);
}
