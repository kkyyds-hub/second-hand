package com.demo.service.serviceimpl;

import com.demo.entity.Address;
import com.demo.mapper.AddressMapper;
import com.demo.service.AddressService;
import com.demo.vo.address.AddressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class AddressServiceImpl implements AddressService {

    @Autowired
    private AddressMapper addressMapper;
    @Override
    public List<AddressVO> listAddresses(Long userId) {
        log.info("获取用户地址, 用户ID: {}", userId);

        // 1. 查询用户的所有地址
        List<Address> addresses = addressMapper.findByUserId(userId);

        // 2. 如果没有找到地址，可以返回空列表或者特殊提示
        if (addresses.isEmpty()) {
            log.info("用户没有地址信息");
            return List.of(); // 返回空列表
        }

        // 3. 将地址实体转换为 AddressVO
        List<AddressVO> addressVOs = addresses.stream()
                .map(this::convertToAddressVO)
                .collect(Collectors.toList());

        // 4. 返回地址列表
        return addressVOs;
    }

    @Override
    public AddressVO getDefaultAddress(Long currentUserId) {
        log.info("查询默认地址, 用户ID: {}", currentUserId);
        Address address = addressMapper.findDefaultByUserId(currentUserId);
        if (address == null) {
            address = addressMapper.findLatestByUserId(currentUserId);
        }
        return address == null ? null : convertToAddressVO(address);
    }

    private AddressVO convertToAddressVO(Address address) {
        AddressVO addressVO = new AddressVO();
        addressVO.setId(address.getId());
        addressVO.setReceiverName(address.getReceiverName());
        addressVO.setMobile(address.getMobile());
        addressVO.setProvinceName(address.getProvinceName());
        addressVO.setCityName(address.getCityName());
        addressVO.setDistrictName(address.getDistrictName());
        addressVO.setDetailAddress(address.getDetailAddress());
        addressVO.setIsDefault(address.getIsDefault());
        return addressVO;
    }

}
