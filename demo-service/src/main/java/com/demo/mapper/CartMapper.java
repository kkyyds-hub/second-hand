package com.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.demo.dto.cart.CartItemRow;
import com.demo.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车数据访问接口。
 *
 * 简单 CRUD 复用 MyBatis-Plus BaseMapper；
 * 列表读取需要联表商品与用户实时数据，放在 CartMapper.xml。
 */
@Mapper
public interface CartMapper extends BaseMapper<CartItem> {

    /**
     * 查询当前用户全部购物车项（按加入时间倒序），并实时联表商品与卖家信息。
     *
     * 返回原始行投影（含商品 is_deleted），由 CartService 计算可用性与失效原因。
     * 商品被软删除时仍保留购物车行，避免整表查询失败。
     */
    List<CartItemRow> listCartItems(@Param("userId") Long userId);

    /**
     * 立即购买成功后，幂等删除当前用户对指定商品的购物车项。
     * 存在则删除，不存在返回 0，不报错。
     */
    int deleteByUserAndProduct(@Param("userId") Long userId,
                               @Param("productId") Long productId);
}
