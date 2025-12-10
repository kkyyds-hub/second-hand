package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.dto.user.AddressDTO;
import com.demo.result.Result;
import com.demo.service.AddressService;
import com.demo.vo.address.AddressVO;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/user/addresses")
@Api(tags = "用户收货地址接口")
@Slf4j
public class AddressController {

    @Autowired
    private AddressService addressService;
    @GetMapping
    public Result<List<AddressVO>> listAddresses() {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("获取用户地址列表, 用户ID: {}", currentUserId);
        List<AddressVO> addressVOList = addressService.listAddresses(currentUserId);
        return Result.success(addressVOList);
    }

    @GetMapping("/default")
    public Result<AddressVO> getDefaultAddress() {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("获取用户默认地址, 用户ID: {}", currentUserId);
        AddressVO addressVO = addressService.getDefaultAddress(currentUserId);
        return Result.success(addressVO);
    }

    @PostMapping
    public Result<AddressVO> createAddress(@Valid @RequestBody AddressDTO request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("新增收货地址, 用户ID: {}, 请求: {}", currentUserId, request);
        AddressVO vo = addressService.createAddress(currentUserId, request);
        return Result.success(vo);
    }
    @PutMapping("/{addressId}")
    public Result<AddressVO> updateAddress(@PathVariable Long addressId,
                                          @Valid @RequestBody AddressDTO request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("更新收货地址, 用户ID: {}, 收货地址ID: {}, 请求: {}", currentUserId, addressId, request);
        AddressVO vo = addressService.updateAddress(currentUserId, addressId, request);
        return Result.success(vo);
    }

    @DeleteMapping("/{addressId}")
    public Result<String> deleteAddress(@PathVariable Long addressId) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("删除收货地址, 用户ID: {}, 收货地址ID: {}", currentUserId, addressId);
        addressService.deleteAddress(currentUserId, addressId);
        return Result.success("删除成功");
    }

    @PutMapping("/{addressId}/default")
    public Result<String> setDefaultAddress(@PathVariable Long addressId) {
        Long currentUserId = BaseContext.getCurrentId();
        addressService.setDefaultAddress(currentUserId, addressId);
        return Result.success("设置默认地址成功");
    }
    @GetMapping("/{addressId}")
    public Result<AddressVO> getAddress(@PathVariable Long addressId) {
        Long currentUserId = BaseContext.getCurrentId();
        AddressVO vo = addressService.getAddressById(currentUserId, addressId);
        return Result.success(vo);
    }

}
