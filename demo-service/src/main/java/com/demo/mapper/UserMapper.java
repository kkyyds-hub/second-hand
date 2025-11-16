package com.demo.mapper;

import com.demo.dto.user.UserQueryDTO;
import com.demo.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {


    List<User> selectUsers(UserQueryDTO queryDTO);
}
