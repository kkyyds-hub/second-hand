package com.demo.mapper;

import com.demo.entity.Address;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface AddressMapper {
    /**
     * 查询指定用户的所有收货地址
     * 默认地址优先，其余按更新时间倒序
     */
    List<Address> findByUserId(@Param("userId") Long userId);

    /**
     * 查询指定用户的默认收货地址
     */
    Address findDefaultByUserId(@Param("userId") Long userId);

    /**
     * 查询指定用户最近一条地址记录
     * （可在没有默认地址时作为兜底）
     */
    Address findLatestByUserId(@Param("userId") Long userId);

    void clearDefaultByUserId(@Param("currentUserId") Long currentUserId);

    void insert(@Param("address") Address address);

    Address findById(@Param("id") Long id);

    void update(Address address);

    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    void updateIsDefaultByIdAndUserId(@Param("id") Long id,
                                      @Param("userId") Long userId,
                                      @Param("isDefault") Boolean isDefault);
}
