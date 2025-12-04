package com.demo.mapper;

import com.demo.entity.UserBan;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserBanMapper {

    /**
     * 插入一条封禁记录
     */
    void insertUserBan(UserBan userBan);
}
