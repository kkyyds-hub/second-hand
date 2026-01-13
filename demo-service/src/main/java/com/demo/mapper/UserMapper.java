package com.demo.mapper;

import com.demo.dto.user.UserQueryDTO;
import com.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMapper {


    List<User> selectUsers(UserQueryDTO queryDTO);

    int insertUser(User user);

    User selectByMobile(@Param("mobile") String mobile);

    User selectByEmail(@Param("email") String email);

    int updateStatus(@Param("userId") Long userId,
                     @Param("status") String status,
                     @Param("updateTime") LocalDateTime updateTime);

    User selectById(@Param("userId") Long userId);

    void updateProfile(User user);

    Integer selectIsSellerById(@Param("userId") Long userId);

    void updatePassword(@Param("userId") Long userId,
                        @Param("password") String password,
                        @Param("updateTime") LocalDateTime updateTime);

    void updateEmail(@Param("userId") Long userId,
                     @Param("email") String email,
                     @Param("updateTime") LocalDateTime updateTime);

    void updateMobile(@Param("userId") Long userId,
                      @Param("mobile") String mobile,
                      @Param("updateTime") LocalDateTime updateTime);

    int updateCredit(@Param("userId") Long userId,
                     @Param("creditScore") Integer creditScore,
                     @Param("creditLevel") String creditLevel,
                     @Param("creditUpdatedAt") LocalDateTime creditUpdatedAt);


}