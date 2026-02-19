package com.demo.mapper;


import com.demo.dto.admin.UserViolationDTO;
import com.demo.dto.admin.ViolationStatisticsDTO;
import com.demo.entity.User;
import com.demo.entity.UserViolation;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
/**
 * ViolationMapper 接口。
 */
public interface ViolationMapper {
    /**
     * 新增用户违规记录。
     */
    @Insert("INSERT INTO user_violations (user_id, violation_type, description, evidence, punish, credit, record_time, create_time) " +
            "VALUES (#{userId}, #{violationType}, #{description}, #{evidence}, #{punish}, #{credit}, #{recordTime}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    // 执行违规记录插入。
    int insert(UserViolation violation);

    /**
     * 幂等插入违规记录（依赖唯一键：user_id + violation_type + biz_id）。
     *
     * 说明：
     * - 仅用于“同一业务动作只能处罚一次”的场景（如 ship_timeout）。
     * - 返回 1 表示新增成功，0 表示幂等命中（已存在）。
     */
    @Insert("INSERT IGNORE INTO user_violations (user_id, biz_id, violation_type, description, evidence, punish, credit, record_time, create_time) " +
            "VALUES (#{userId}, #{bizId}, #{violationType}, #{description}, #{evidence}, #{punish}, #{credit}, #{recordTime}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    // 执行幂等违规记录插入。
    int insertIgnore(UserViolation violation);


    /**
     * 按用户 ID 查询违规记录列表。
     */
    @Select("SELECT * FROM user_violations WHERE user_id = #{userId} ORDER BY record_time DESC")
    List<UserViolation> selectByUserId(@Param("userId") Long userId);


    /**
     * 按用户 ID 查询用户信息。
     */
    User SelectById(@Param("userId") Long userId);


    /**
     * 更新用户状态。
     */
    void update(User user);

    /**
     * 查询违规类型统计。
     */
    List<ViolationStatisticsDTO> getViolationStatistics();

    /**
     * 查询指定用户违规明细。
     */
    List<UserViolationDTO> getUserViolations(@Param("userId") Long userId);

}
