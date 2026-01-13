package com.demo.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface CreditStatMapper {

    Long countCompletedAsBuyer(@Param("userId") Long userId);

    Long countCompletedAsSeller(@Param("userId") Long userId);

    Long countCancelledAsBuyer(@Param("userId") Long userId);

    Integer sumViolationCreditDelta(@Param("userId") Long userId);

    Integer countActiveBans(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    Integer sumAdminAdjustDelta(@Param("userId") Long userId);

}
