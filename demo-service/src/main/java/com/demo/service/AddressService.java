package com.demo.service;

import com.demo.vo.address.AddressVO;

import java.util.List;

public interface AddressService {
    List<AddressVO> getAddress(Long currentUserId);
}
