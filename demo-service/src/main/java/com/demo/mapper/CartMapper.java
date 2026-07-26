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
     * 查询单个购物车项并联表商品与卖家信息。
     *
     * 用于加入/幂等返回时回读完整展示字段（含 sellerNickname），
     * 避免返回只有 ID 的半成品结果。
     */
    CartItemRow selectByUserAndProduct(@Param("userId") Long userId,
                                       @Param("productId") Long productId);

    /**
     * 对当前用户行加行锁（FOR UPDATE），作为购物车容量校验的数据库级串行化点。
     *
     * 并发加入不同商品时，先锁住同一 users 行的事务串行执行容量检查，
     * 保证 50 项上限在并发下仍然成立。返回用户 ID；用户不存在时返回 null。
     */
    Long selectUserIdForUpdate(@Param("userId") Long userId);

    /**
     * 立即购买成功后，幂等删除当前用户对指定商品的购物车项。
     * 存在则删除，不存在返回 0，不报错。
     */
    int deleteByUserAndProduct(@Param("userId") Long userId,
                               @Param("productId") Long productId);
}
