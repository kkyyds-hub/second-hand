package com.demo.service;

import com.demo.dto.admin.AdminCreateUserRequest;
import com.demo.dto.user.AvatarUploadConfigRequest;
import com.demo.dto.user.AvatarUploadTicketRequest;
import com.demo.dto.user.BindEmailRequest;
import com.demo.dto.user.BindPhoneRequest;
import com.demo.dto.user.ChangePasswordRequest;
import com.demo.dto.user.UnbindContactRequest;
import com.demo.dto.user.UpdateProfileRequest;
import com.demo.dto.user.UserQueryDTO;
import com.demo.result.PageResult;
import com.demo.vo.AvatarUploadConfigVO;
import com.demo.vo.UserVO;

import javax.validation.Valid;
import java.io.InputStream;
import java.time.LocalDateTime;

/**
 * 用户服务接口。
 * 负责管理端用户分页、账号状态管理，以及用户中心资料维护等能力。
 */
public interface UserService {
    /**
     * 分页查询用户列表（支持关键字、状态、角色等条件）。
     */
    PageResult<UserVO> getUserPage(UserQueryDTO queryDTO);

    /**
     * 更新当前登录用户的基础资料。
     */
    UserVO updateProfile(UpdateProfileRequest request);

    /**
     * 生成头像上传配置。
     */
    AvatarUploadConfigVO generateAvatarUploadConfig(@Valid AvatarUploadConfigRequest request, String requestBaseUrl);

    /**
     * 上传头像并返回可访问地址。
     */
    String uploadAvatar(@Valid AvatarUploadTicketRequest request,
                        String contentType,
                        long contentLength,
                        InputStream inputStream,
                        String requestBaseUrl);

    /**
     * 修改当前用户密码（支持旧密码或验证码校验）。
     */
    void changePassword(ChangePasswordRequest request);

    /**
     * 绑定手机号。
     */
    UserVO bindPhone(BindPhoneRequest request);

    /**
     * 绑定邮箱。
     */
    UserVO bindEmail(BindEmailRequest request);

    /**
     * 解绑手机号。
     */
    void unbindPhone(@Valid UnbindContactRequest request);

    /**
     * 解绑邮箱。
     */
    void unbindEmail(@Valid UnbindContactRequest request);

    /**
     * 校验指定用户是否具备卖家身份。
     */
    void requireSeller(Long userId);

    /**
     * 管理员手动创建用户档案。
     */
    UserVO createAdminUser(AdminCreateUserRequest request);

    /**
     * 封禁用户（兼容旧接口，不携带封禁原因）。
     */
    String banUser(Long userId);

    /**
     * 封禁用户（支持接收封禁原因）。
     */
    String banUser(Long userId, String reason);

    /**
     * 解封用户。
     */
    String unbanUser(Long userId);

    /**
     * 导出用户 CSV。
     */
    String exportUsersCSV(String keyword, LocalDateTime startTime, LocalDateTime endTime);
}
