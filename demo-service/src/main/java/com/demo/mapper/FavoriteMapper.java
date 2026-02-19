package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.entity.Favorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
/**
 * FavoriteMapper 接口。
 */
public interface FavoriteMapper extends BaseMapper<Favorite> {
    /**
     * 恢复被逻辑删除的收藏记录：is_deleted 1 -> 0
     * 返回影响行数：>0 表示确实恢复了一条
     */
    @Update("""
    UPDATE favorites
    SET is_deleted = 0,
        create_time = NOW(),
        update_time = NOW()
    WHERE user_id = #{userId}
      AND product_id = #{productId}
      AND is_deleted = 1
""")
    int restoreDeleted(@Param("userId") Long userId,
                       @Param("productId") Long productId);

    /**
     * 取消收藏：is_deleted 0 -> 1（幂等）
     * 返回影响行数：0 说明本来就没收藏/已取消
     */
    @Update("""
        UPDATE favorites
        SET is_deleted = 1, update_time = NOW()
        WHERE user_id = #{userId}
          AND product_id = #{productId}
          AND is_deleted = 0
    """)
    int softDelete(@Param("userId") Long userId,
                   @Param("productId") Long productId);
}
