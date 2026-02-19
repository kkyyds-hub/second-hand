package com.demo.service;

import com.demo.dto.user.AddressDTO;
import com.demo.vo.address.AddressVO;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import java.util.List;

/**
 * 用户地址领域服务接口。
 */
public interface AddressService {

    /**
     * 查询指定用户的地址列表。
     */
    List<AddressVO> listAddresses(Long userId);

    /**
     * 查询指定用户的默认地址。
     */
    AddressVO  getDefaultAddress(Long currentUserId );

    /**
     * 为指定用户新增地址。
     */
    AddressVO createAddress(Long currentUserId, @Valid AddressDTO request);

    /**
     * 修改指定用户的地址。
     */
    AddressVO updateAddress(Long currentUserId, Long addressId, @Valid AddressDTO request);

    /**
     * 删除指定用户的地址。
     */
    void deleteAddress(Long currentUserId, Long addressId);

    /**
     * 在一个事务中切换指定用户的默认地址。
     */
    @Transactional
    void setDefaultAddress(Long currentUserId, Long addressId);

    /**
     * 按地址 ID 查询指定用户的地址详情。
     */
    AddressVO getAddressById(Long currentUserId, Long addressId);
}
