package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.Review;
import org.apache.ibatis.annotations.Mapper;

@Mapper
/**
 * ReviewMapper 接口。
 */
public interface ReviewMapper extends BaseMapper<Review> {
}
