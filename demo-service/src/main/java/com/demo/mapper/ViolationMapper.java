package com.demo.mapper;


import com.demo.entity.User;
import com.demo.entity.UserViolation;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface ViolationMapper {
    @Insert("INSERT INTO user_violations (user_id, violation_type, description, evidence, punish, credit, record_time, create_time) " +
            "VALUES (#{userId}, #{violationType}, #{description}, #{evidence}, #{punish}, #{credit}, #{recordTime}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserViolation violation);


    @Select("SELECT * FROM user_violations WHERE user_id = #{userId} ORDER BY record_time DESC")
    List<UserViolation> selectByUserId(Long userId);


    User SelectById(@Param("userId") Long userId);


    void update(User user);

    List<Map<String, Object>> getViolationStatistics();

    List<Map<String, Object>> getUserViolations(Long userId);
}
