package com.demo.controller.admin;

import com.demo.constant.JwtClaimsConstant;
import com.demo.dto.auth.AuthResponse;
import com.demo.dto.user.PasswordLoginRequest;
import com.demo.entity.User;
import com.demo.exception.BusinessException;
import com.demo.mapper.UserMapper;
import com.demo.properties.JwtProperties;
import com.demo.result.Result;
import com.demo.utils.JwtUtil;
import com.demo.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端员工账号接口。
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
public class EmployeeController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 管理员登录。
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody PasswordLoginRequest req) {
        String loginId = req.getLoginId();
        if (!StringUtils.hasText(loginId)) {
            throw new BusinessException("账号不能为空");
        }

        User user = resolveUser(loginId.trim());
        if (user == null) {
            throw new BusinessException("账号不存在");
        }

        // 最小实现：通过用户名前缀校验是否管理员。
        if (user.getUsername() == null || !user.getUsername().toLowerCase().startsWith("admin")) {
            throw new BusinessException("非管理员账号");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, user.getId());
        String token = JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims);

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);

        return Result.success(new AuthResponse(token, vo));
    }

    /**
     * 根据登录标识解析用户（邮箱或手机号）。
     */
    private User resolveUser(String loginId) {
        if (loginId.contains("@")) {
            return userMapper.selectByEmail(loginId);
        }
        return userMapper.selectByMobile(loginId);
    }
}
