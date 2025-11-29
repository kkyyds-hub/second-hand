package com.demo.mapper;

import com.demo.dto.user.UserQueryDTO;
import com.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

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

    User SelectById(Long userId);

    void updateProfile(User user);
}