package com.demo.service;

import com.demo.dto.user.AddressDTO;
import com.demo.vo.address.AddressVO;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Valid;
import java.util.List;

public interface AddressService {
    List<AddressVO> listAddresses(Long userId);

    AddressVO  getDefaultAddress(Long currentUserId );

    AddressVO createAddress(Long currentUserId, @Valid AddressDTO request);

    AddressVO updateAddress(Long currentUserId, Long addressId, @Valid AddressDTO request);

    void deleteAddress(Long currentUserId, Long addressId);

    @Transactional
    void setDefaultAddress(Long currentUserId, Long addressId);

    AddressVO getAddressById(Long currentUserId, Long addressId);
}
