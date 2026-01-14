package com.demo.mapper;

import com.demo.entity.UserCreditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserCreditLogMapper {

    /**
     * 插入信用分变更流水
     *
     * @param log 信用分变更流水实体
     * @return 影响行数
     */
    int insert(UserCreditLog log);

    /**
     * 根据用户ID查询信用分变更流水列表（按创建时间倒序）
     *
     * @param userId 用户ID
     * @return 信用分变更流水列表
     */
    List<UserCreditLog> listByUserId(@Param("userId") Long userId);
}

