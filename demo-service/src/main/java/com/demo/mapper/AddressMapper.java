package com.demo.mapper;

import com.demo.entity.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AddressMapper {

    /** 查询指定用户的所有收货地址：默认优先，其余按更新时间倒序 */
    List<Address> findByUserId(@Param("userId") Long userId);

    /** 查询指定用户的默认收货地址 */
    Address findDefaultByUserId(@Param("userId") Long userId);

    /** 查询指定用户最近一条地址记录（无默认时兜底） */
    Address findLatestByUserId(@Param("userId") Long userId);

    /**
     * 清空指定用户的默认地址（把 is_default=1 的改成 0）
     * 注意：XML 用的是 #{userId}，所以这里 @Param 也必须叫 userId
     */
    void clearDefaultByUserId(@Param("userId") Long userId);

    /**
     * 新增地址
     * 注意：XML 里用的是 #{userId}/#{receiverName}...（直接字段），所以这里不要用 @Param("address")
     */
    void insert(Address address);

    Address findById(@Param("id") Long id);

    /**
     * 更新地址
     * 注意：同 insert，XML 使用的是直接字段
     */
    void update(Address address);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    void updateIsDefaultByIdAndUserId(@Param("id") Long id,
                                      @Param("userId") Long userId,
                                      @Param("isDefault") Boolean isDefault);
}
