package com.demo.service.serviceimpl;

import com.demo.constant.MessageConstant;
import com.demo.context.BaseContext;
import com.demo.dto.user.*;
import com.demo.entity.User;
import com.demo.mapper.UserMapper;
import com.demo.result.PageResult;
import com.demo.service.UserService;
import com.demo.vo.AvatarUploadConfigVO;
import com.demo.vo.UserVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@Slf4j
public class UserServiceImpl implements UserService {
    private static final String SMS_CODE_KEY_PREFIX = "auth:sms:code:";
    private static final String EMAIL_CODE_KEY_PREFIX = "auth:email:code:";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResult<UserVO> getUserPage(UserQueryDTO queryDTO) {
        log.info("分页查询用户: {}", queryDTO);
        PageHelper.startPage(queryDTO.getPage(), queryDTO.getSize());

        // 2. 执行普通查询（PageHelper会自动分页）
        List<User> userList = userMapper.selectUsers(queryDTO);

        // 3. 获取分页信息
        PageInfo<User> pageInfo = new PageInfo<>(userList);

        // 4. 转换为VO
        List<UserVO> userVOList = convertToVOList(userList);

        // 5. 返回结果
        return new PageResult<>(
                userVOList,
                pageInfo.getTotal(),
                queryDTO.getPage(),
                queryDTO.getSize()
        );
    }

    @Override
    public UserVO updateProfile(UpdateProfileRequest request) {
        log.info("更新用户信息: {}", request);
        User user = userMapper.SelectById(request.getUserId());
        if (user == null){
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        NicknameValidation(request.getNickname());
        AvatarValidation(request.getAvatar());
        BioValidation(request.getBio());
        user.setId(request.getUserId());
        user.setNickname(request.getNickname());
        user.setAvatar(request.getAvatar());
        user.setBio(request.getBio());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateProfile(user);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public AvatarUploadConfigVO generateAvatarUploadConfig(AvatarUploadConfigRequest request) {
        String normalizedFileName = request.getFileName().trim();
        String lowerFileName = normalizedFileName.toLowerCase();

        if (!lowerFileName.endsWith(".jpg") && !lowerFileName.endsWith(".jpeg") && !lowerFileName.endsWith(".png")) {
            throw new RuntimeException("文件名需以 .jpg/.jpeg/.png 结尾");
        }

        // 补充一次 contentType 与扩展名的匹配校验
        boolean isPng = lowerFileName.endsWith(".png");
        if (isPng && !"image/png".equalsIgnoreCase(request.getContentType())) {
            throw new RuntimeException("PNG 头像需使用 image/png 上传");
        }
        if (!isPng && !"image/jpeg".equalsIgnoreCase(request.getContentType())) {
            throw new RuntimeException("JPEG 头像需使用 image/jpeg 上传");
        }

        String suffix = lowerFileName.substring(lowerFileName.lastIndexOf('.'));
        String objectKey = "avatars/" + UUID.randomUUID() + suffix;

        // 这里使用示例 OSS 域名生成资源与上传地址；实际项目中请替换为真实存储配置
        String resourceUrl = "https://oss.example.com/" + objectKey;
        String signature = UUID.randomUUID().toString().replace("-", "");
        OffsetDateTime expireAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5);
        String uploadUrl = resourceUrl + "?signature=" + signature + "&expires=" + expireAt.toEpochSecond();

        return AvatarUploadConfigVO.builder()
                .uploadUrl(uploadUrl)
                .resourceUrl(resourceUrl)
                .expiresIn(300)
                .extraHeaders(Map.of("content-type", request.getContentType()))
                .build();
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        User user = userMapper.SelectById(currentUserId);
        if (user == null) {
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        boolean verified = false;
        if (StringUtils.isNotBlank(request.getOldPassword())) {
            if (!user.getPassword().equals(request.getOldPassword())){
                throw new RuntimeException(MessageConstant.PASSWORD_ERROR);
            }
            verified = true;
        }
        if (StringUtils.isNotBlank(request.getVerifyChannel())){
            String code = request.getCode();
            String channel = request.getVerifyChannel().toLowerCase();
            if (StringUtils.isNotBlank( code)){
                throw new RuntimeException("验证码不能为空");
            }
            switch ( channel){
                case "email":
                    verifySmsCode(user, code);
                    break;
                case "phone":
                    verifyEmailCode(user, code);
                    break;
                default:
                    throw new RuntimeException("不支持的验证渠道");
            }
            verified = true;
            }
        if (!verified) {
            throw new RuntimeException("请提供当前密码或验证码进行验证");
        }
        userMapper.updatePassword(currentUserId, request.getNewPassword(), LocalDateTime.now());
    }

    @Override
    public UserVO bindPhone(BindPhoneRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        User user = userMapper.SelectById(currentUserId);
        if (user == null) {
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        String mobile = request.getValue();
        verifySmsCode(user, request.getVerifyCode());
        User existed = userMapper.selectByMobile(mobile);
        if (existed != null && existed.getId().equals(currentUserId)) {
            throw new RuntimeException("手机号已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateMobile(currentUserId, mobile, now);
        stringRedisTemplate.delete(SMS_CODE_KEY_PREFIX + mobile);
        user.setMobile(mobile);
        user.setUpdateTime(now);
        return toUserVO(user);
    }

    @Override
    public UserVO bindEmail(BindEmailRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        User user = userMapper.SelectById(currentUserId);
        if (user == null) {
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        String email = request.getValue();
        verifyEmailCode(user, request.getVerifyCode());
        User existed = userMapper.selectByEmail(email);
        if (existed != null && existed.getId().equals(currentUserId)) {
            throw new RuntimeException("邮箱已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateEmail(currentUserId, email, now);
        stringRedisTemplate.delete(EMAIL_CODE_KEY_PREFIX + email);
        user.setEmail(email);
        user.setUpdateTime(now);
        return toUserVO(user);
    }

    @Override
    public void unbindPhone(UnbindContactRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        User user = userMapper.SelectById(currentUserId);
        if (user == null) {
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        if (StringUtils.isBlank(user.getMobile())) {
            throw new RuntimeException("当前账号未绑定手机号");
        }

        verifyUnbindRequest(user, request);
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateMobile(currentUserId, null, now);
        stringRedisTemplate.delete(SMS_CODE_KEY_PREFIX + user.getMobile());
    }

    @Override
    public void unbindEmail(UnbindContactRequest request) {
        Long currentUserId = BaseContext.getCurrentId();
        User user = userMapper.SelectById(currentUserId);
        if (user == null) {
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        if (StringUtils.isBlank(user.getEmail())) {
            throw new RuntimeException("当前账号未绑定邮箱");
        }

        verifyUnbindRequest(user, request);
        LocalDateTime now = LocalDateTime.now();
        userMapper.updateEmail(currentUserId, null, now);
        stringRedisTemplate.delete(EMAIL_CODE_KEY_PREFIX + user.getEmail());
    }


    private List<UserVO> convertToVOList(List<User> userList) {
        return userList.stream().map(user -> {
            UserVO userVO = new UserVO();
            BeanUtils.copyProperties(user, userVO);
            return userVO;
        }).collect(Collectors.toList());
    }

    private void BioValidation(@Size(max = 150, message = "简介不能超过150个字符") String bio) {
        if (bio == null) {
            return;
        }
        List<String> blockedWords = Arrays.asList("admin", "管理员", "违规");
        for (String word : blockedWords) {
            //LowerCase(): 将字符串转换为小写,contains(): 判断字符串中是否包含指定的子串
            if (bio.toLowerCase().contains(word.toLowerCase())) {
                throw new RuntimeException("昵称包含敏感词，请更换");
            }
        }
    }

    private void AvatarValidation(String avatar) {
        if (StringUtils.isBlank(avatar)) {
            return;
        }
        String lower = avatar.toLowerCase();
        boolean isHttp = lower.startsWith("http://") || lower.startsWith("https://");
        boolean supportedExt = lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
        if (!isHttp || !supportedExt) {
            throw new RuntimeException("头像地址需为可访问的JPG/PNG链接");
        }
    }

    private void NicknameValidation(@Size(min = 1, max = 20, message = "昵称长度需在1-20个字符内") String nickname) {
        List<String> blockedWords = Arrays.asList("admin", "管理员", "违规");
        for (String word : blockedWords) {
            //LowerCase(): 将字符串转换为小写,contains(): 判断字符串中是否包含指定的子串
            if (nickname.toLowerCase().contains(word.toLowerCase())) {
                throw new RuntimeException("昵称包含敏感词，请更换");
            }
        }
    }
    private void verifyEmailCode(User user, String code) {
        if (StringUtils.isBlank(user.getMobile())) {
            throw new RuntimeException("未绑定手机号，无法使用短信验证");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(SMS_CODE_KEY_PREFIX + user.getMobile());
        if (StringUtils.isBlank(cacheCode) || !cacheCode.equals(code)) {
            throw new RuntimeException("验证码错误或已过期");
        }
    }

    private void verifySmsCode(User user, String code) {
        if (StringUtils.isBlank(user.getEmail())) {
            throw new RuntimeException("未绑定邮箱，无法使用邮箱验证");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(EMAIL_CODE_KEY_PREFIX + user.getEmail());
        if (StringUtils.isBlank(cacheCode) || !cacheCode.equals(code)) {
            throw new RuntimeException("验证码错误或已过期");
        }
    }
    private void verifyUnbindRequest(User user, UnbindContactRequest request) {
        boolean verified = false;
        if (StringUtils.isNotBlank(request.getCurrentPassword())) {
            if (!request.getCurrentPassword().equals(user.getPassword())) {
                throw new RuntimeException("当前密码不正确");
            }
            verified = true;
        }

        String channel = request.getVerifyChannel();
        if (StringUtils.isBlank(channel)) {
            throw new RuntimeException("请选择验证方式");
        }

        String code = request.getVerifyCode();
        if (StringUtils.isBlank(code)) {
            throw new RuntimeException("验证码不能为空");
        }

        switch (channel.toLowerCase()) {
            case "phone":
                verifySmsCode(user, code);
                break;
            case "email":
                verifyEmailCode(user, code);
                break;
            default:
                throw new RuntimeException("不支持的验证渠道");
        }
        verified = true;

        if (!verified) {
            throw new RuntimeException("请提供当前密码或验证码进行验证");
        }
    }
    private UserVO toUserVO(User user) {
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }


}