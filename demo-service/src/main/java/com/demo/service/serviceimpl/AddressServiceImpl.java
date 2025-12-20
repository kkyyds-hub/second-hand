package com.demo.service.serviceimpl;

import com.demo.dto.user.AddressDTO;
import com.demo.entity.Address;
import com.demo.exception.BusinessException;
import com.demo.mapper.AddressMapper;
import com.demo.service.AddressService;
import com.demo.vo.address.AddressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
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

        List<Address> addresses = addressMapper.findByUserId(userId);
        if (addresses == null || addresses.isEmpty()) {
            log.info("用户没有地址信息");
            return List.of();
        }

        return addresses.stream()
                .map(this::convertToAddressVO)
                .collect(Collectors.toList());
    }

    @Override
    public AddressVO getDefaultAddress(Long currentUserId) {
        log.info("查询默认地址(兜底最近一条), 用户ID: {}", currentUserId);

        Address address = findDefaultOrLatestAddress(currentUserId);
        return address == null ? null : convertToAddressVO(address);
    }

    /**
     * 兜底策略：
     * 1) 优先返回默认地址（is_default = 1）
     * 2) 若不存在默认地址，则返回最近一条地址记录（兜底）
     */
    private Address findDefaultOrLatestAddress(Long userId) {
        Address defaultAddress = addressMapper.findDefaultByUserId(userId);
        if (defaultAddress != null) {
            return defaultAddress;
        }
        return addressMapper.findLatestByUserId(userId);
    }

    @Override
    public AddressVO createAddress(Long currentUserId, AddressDTO request) {
        log.info("新增收货地址, 用户ID: {}, 请求: {}", currentUserId, request);

        Address address = new Address();
        address.setUserId(currentUserId);
        addressSet(request, address);

        // 去除首尾空格
        address.setDetailAddress(request.getDetailAddress().trim());

        Boolean isDefault = request.getIsDefault();
        if (Boolean.TRUE.equals(isDefault)) {
            // 清掉当前用户的旧默认地址
            addressMapper.clearDefaultByUserId(currentUserId);
            address.setIsDefault(true);
        } else {
            address.setIsDefault(false);
        }

        addressMapper.insert(address);

        log.info("新增收货地址成功, 用户ID: {}, 地址ID: {}", currentUserId, address.getId());
        return convertToAddressVO(address);
    }

    @Override
    public AddressVO updateAddress(Long currentUserId, Long addressId, AddressDTO request) {
        log.info("更新收货地址, 用户ID: {}, 地址ID: {}", currentUserId, addressId);

        // 1. 查询并校验归属
        Address address = addressMapper.findById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), currentUserId)) {
            throw new BusinessException("地址不存在或无权修改");
        }

        // 2. 覆盖基础字段
        addressSet(request, address);

        // 3. 处理详细地址
        if (StringUtils.hasText(request.getDetailAddress())) {
            address.setDetailAddress(request.getDetailAddress().trim());
        } else {
            // 理论上不会走到这里，因为 DTO 上有 @NotBlank，仅防御性判断
            address.setDetailAddress(request.getDetailAddress());
        }

        // 4. 处理 isDefault 三种情况
        Boolean newIsDefault = request.getIsDefault();     // 本次请求想要的状态（可能为 null）
        Boolean oldIsDefault = address.getIsDefault();     // 数据库里原来的状态

        if (newIsDefault != null) {
            if (Boolean.TRUE.equals(newIsDefault) && !Boolean.TRUE.equals(oldIsDefault)) {
                // 之前不是默认，现在要变成默认 → 先清掉该用户其他默认
                addressMapper.clearDefaultByUserId(currentUserId);
                address.setIsDefault(true);
            } else if (Boolean.FALSE.equals(newIsDefault) && Boolean.TRUE.equals(oldIsDefault)) {
                // 之前是默认，现在显式改成非默认
                address.setIsDefault(false);
            }
            // 如果 newIsDefault 和 oldIsDefault 一样，就不动 isDefault
        }

        address.setUpdateTime(LocalDateTime.now());

        // 5. 落库
        addressMapper.update(address);

        // 6. 回显给前端
        return convertToAddressVO(address);
    }

    @Override
    public void deleteAddress(Long currentUserId, Long addressId) {
        log.info("删除收货地址, 用户ID: {}, 收货地址ID: {}", currentUserId, addressId);

        // 1. 查询并校验归属
        Address address = addressMapper.findById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), currentUserId)) {
            throw new BusinessException("地址不存在或无权修改");
        }

        int rows = addressMapper.deleteByIdAndUserId(addressId, currentUserId);
        if (rows == 0) {
            throw new BusinessException("地址不存在或无权修改");
        }
    }

    /** 公共字段赋值方法，避免重复代码 */
    private void addressSet(AddressDTO request, Address address) {
        address.setReceiverName(request.getReceiverName());
        address.setMobile(request.getMobile());
        address.setProvinceCode(request.getProvinceCode());
        address.setProvinceName(request.getProvinceName());
        address.setCityCode(request.getCityCode());
        address.setCityName(request.getCityName());
        address.setDistrictCode(request.getDistrictCode());
        address.setDistrictName(request.getDistrictName());
    }

    /** Address -> AddressVO 的转换 */
    private AddressVO convertToAddressVO(Address address) {
        AddressVO addressVO = new AddressVO();
        addressVO.setId(address.getId());
        addressVO.setReceiverName(address.getReceiverName());
        addressVO.setMobile(address.getMobile());
        addressVO.setProvinceName(address.getProvinceName());
        addressVO.setProvinceCode(address.getProvinceCode());
        addressVO.setCityName(address.getCityName());
        addressVO.setCityCode(address.getCityCode());
        addressVO.setDistrictName(address.getDistrictName());
        addressVO.setDistrictCode(address.getDistrictCode());
        addressVO.setDetailAddress(address.getDetailAddress());
        addressVO.setIsDefault(address.getIsDefault());
        return addressVO;
    }

    @Transactional
    @Override
    public void setDefaultAddress(Long currentUserId, Long addressId) {
        log.info("设置默认收货地址, 用户ID: {}, 地址ID: {}", currentUserId, addressId);

        // 1. 查询并校验归属
        Address address = addressMapper.findById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), currentUserId)) {
            throw new BusinessException("地址不存在或无权设置为默认地址");
        }

        // 如果本来就是默认地址，直接返回，避免不必要的写操作
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            return;
        }

        // 2. 先清除该用户原有的默认地址
        addressMapper.clearDefaultByUserId(currentUserId);

        // 3. 将当前地址设为默认
        addressMapper.updateIsDefaultByIdAndUserId(addressId, currentUserId, true);

        log.info("设置默认地址成功, 用户ID: {}, 地址ID: {}", currentUserId, addressId);
    }

    @Override
    public AddressVO getAddressById(Long currentUserId, Long addressId) {
        Address address = addressMapper.findById(addressId);
        if (address == null || !Objects.equals(address.getUserId(), currentUserId)) {
            throw new BusinessException("地址不存在或无权查看该地址");
        }
        return convertToAddressVO(address);
    }
}
