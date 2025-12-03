package com.demo.mapper;


import com.demo.entity.User;
import com.demo.entity.UserViolation;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ViolationMapper {
    @Insert("INSERT INTO user_violation (user_id, violation_type, description, punishment_result, credit_score_change, create_time) " +
            "VALUES (#{userId}, #{violationType}, #{description}, #{punish}, #{credit}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserViolation violation);

    @Select("SELECT * FROM user_violation WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<UserViolation> selectByUserId(Long userId);

    User SelectById(Long userId);

    void update(User user);

    List<Map<String, Object>> getViolationStatistics();

    List<Map<String, Object>> getUserViolations(Long userId);
}
