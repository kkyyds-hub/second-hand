package com.demo.interceptor;

import com.demo.constant.JwtClaimsConstant;
import com.demo.context.BaseContext;
import com.demo.entity.User;
import com.demo.enumeration.UserStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.UserMapper;
import com.demo.properties.JwtProperties;
import com.demo.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

/**
 * jwt令牌校验的拦截器
 */
@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserMapper userMapper;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    private static final String DAU_KEY_PREFIX = "dau:";
    /**
     * 校验jwt
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 不是 Controller 方法，直接放行（比如访问静态资源）
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        // 2. 从请求头获取用户端 token
        String token = request.getHeader(jwtProperties.getUserTokenName());
        if (!StringUtils.hasText(token)) {
            log.warn("用户端令牌为空，请求被拒绝");
            response.setStatus(401);
            return false;
        }

        try {
            // 3. 解析 JWT
            log.info("用户端 jwt 校验: {}", token);
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            if (claims == null) {
                // token 过期/非法时，JwtUtil 返回 null
                response.setStatus(401);
                return false;
            }

            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            log.info("当前用户id: {}", userId);

            // 4. 查询数据库中的用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                throw new BusinessException("用户不存在或已被删除");
            }

            // 5. 根据用户状态做风控拦截
            UserStatus status = UserStatus.from(user.getStatus());
            if (status == UserStatus.BANNED) {
                // Day13 冻结：banned 允许读（GET），禁止写（POST/PUT/DELETE）
                String method = request.getMethod();
                if (!"GET".equalsIgnoreCase(method)) {
                    throw new BusinessException("账号已被封禁，如有疑问请联系客服");
                }
            }
            if (status == UserStatus.FROZEN) {
                throw new BusinessException("账号已被暂时冻结，请稍后再试或联系管理员");
            }
            if (status == UserStatus.INACTIVE) {
                throw new BusinessException("账号未激活，请先完成激活");
            }

            // 6. 状态正常，保存当前用户ID到上下文，放行
            BaseContext.setCurrentId(userId);

            // Day13 DAU 口径：用户调用任一 /user/** GET 接口视为当天活跃（Redis SET 去重）
            if ("GET".equalsIgnoreCase(request.getMethod()) && stringRedisTemplate != null) {
                String key = DAU_KEY_PREFIX + LocalDate.now();
                try {
                    stringRedisTemplate.opsForSet().add(key, String.valueOf(userId));
                } catch (Exception e) {
                    log.warn("DAU 记录失败: userId={}, err={}", userId, e.getMessage());
                }
            }
            return true;

        } catch (BusinessException e) {
            // 业务异常交给全局异常处理器统一返回 JSON
            throw e;
        } catch (Exception ex) {
            // token 解析等非业务异常，统一当作 401 处理
            log.warn("用户端 jwt 解析失败: {}", ex.getMessage());
            response.setStatus(401);
            return false;
        }
    }
}
