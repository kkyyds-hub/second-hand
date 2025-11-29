package com.demo.service.serviceimpl;

import com.demo.constant.MessageConstant;
import com.demo.dto.user.AvatarUploadConfigRequest;
import com.demo.dto.user.UpdateProfileRequest;
import com.demo.dto.user.UserQueryDTO;
import com.demo.entity.User;
import com.demo.mapper.UserMapper;
import com.demo.result.PageResult;
import com.demo.service.UserService;
import com.demo.vo.AvatarUploadConfigVO;
import com.demo.vo.UserVO;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private UserMapper userMapper;

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

}