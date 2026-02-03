package com.demo.mapper;

import com.demo.entity.PointsLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Day13 Step8 - 积分 Mapper
 */
@Mapper
public interface PointsMapper {

    /**
     * 插入积分流水（唯一键冲突时会抛异常）
     */
    int insertPoints(PointsLedger ledger);

    /**
     * 查询用户积分流水（分页由 PageHelper 处理）
     */
    List<PointsLedger> listPointsByUserId(@Param("userId") Long userId);

    /**
     * 查询用户积分总额
     */
    Integer sumPointsByUserId(@Param("userId") Long userId);
}
