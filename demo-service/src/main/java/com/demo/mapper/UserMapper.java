package com.demo.mapper;

import com.demo.dto.user.UserQueryDTO;
import com.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户数据访问接口。
 */
@Mapper
public interface UserMapper {


    /**
     * 按条件分页查询用户列表。
     */
    List<User> selectUsers(UserQueryDTO queryDTO);

    /**
     * 新增用户。
     */
    int insertUser(User user);

    /**
     * 按手机号查询用户。
     */
    User selectByMobile(@Param("mobile") String mobile);

    /**
     * 按邮箱查询用户。
     */
    User selectByEmail(@Param("email") String email);

    /**
     * 更新用户状态与更新时间。
     */
    int updateStatus(@Param("userId") Long userId,
                     @Param("status") String status,
                     @Param("updateTime") LocalDateTime updateTime);

    /**
     * 按用户 ID 查询用户。
     */
    User selectById(@Param("userId") Long userId);

    /**
     * 更新用户资料。
     */
    void updateProfile(User user);

    /**
     * 查询用户是否卖家标识。
     */
    Integer selectIsSellerById(@Param("userId") Long userId);

    /**
     * 更新用户密码。
     */
    void updatePassword(@Param("userId") Long userId,
                        @Param("password") String password,
                        @Param("updateTime") LocalDateTime updateTime);

    /**
     * 更新用户邮箱。
     */
    void updateEmail(@Param("userId") Long userId,
                     @Param("email") String email,
                     @Param("updateTime") LocalDateTime updateTime);

    /**
     * 更新用户手机号。
     */
    void updateMobile(@Param("userId") Long userId,
                      @Param("mobile") String mobile,
                      @Param("updateTime") LocalDateTime updateTime);

    /**
     * 更新用户信用分与信用等级。
     */
    int updateCredit(@Param("userId") Long userId,
                     @Param("creditScore") Integer creditScore,
                     @Param("creditLevel") String creditLevel,
                     @Param("creditUpdatedAt") LocalDateTime creditUpdatedAt);

    /**
     * Day13 Step7 - 用户导出（全部用户）
     */
    List<User> exportAllUsers(@Param("keyword") String keyword,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

}

