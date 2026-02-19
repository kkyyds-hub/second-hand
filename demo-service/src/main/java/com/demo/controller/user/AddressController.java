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

/**
 * 用户收货地址管理接口。
 * 覆盖地址列表、默认地址、新增、修改、删除、设为默认等操作。
 */
@RestController
@RequestMapping("/user/addresses")
@Api(tags = "用户收货地址接口")
@Slf4j
public class AddressController {

    @Autowired
    private AddressService addressService;
    /**
     * 查询当前用户的地址列表。
     */
    @GetMapping
    public Result<List<AddressVO>> listAddresses() {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("获取用户地址列表, 用户 ID: {}", currentUserId);
        List<AddressVO> addressVOList = addressService.listAddresses(currentUserId);
        return Result.success(addressVOList);
    }

    /**
     * 查询当前用户的默认地址。
     */
    @GetMapping("/default")
    public Result<AddressVO> getDefaultAddress() {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("获取用户默认地址, 用户 ID: {}", currentUserId);
        AddressVO addressVO = addressService.getDefaultAddress(currentUserId);
        return Result.success(addressVO);
    }

    /**
     * 为当前用户新增收货地址。
     */
    @PostMapping
    public Result<AddressVO> createAddress(@Valid @RequestBody AddressDTO request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("新增收货地址, 用户 ID: {}, 请求: {}", currentUserId, request);
        AddressVO vo = addressService.createAddress(currentUserId, request);
        return Result.success(vo);
    }
    /**
     * 修改当前用户名下的指定地址。
     */
    @PutMapping("/{addressId}")
    public Result<AddressVO> updateAddress(@PathVariable Long addressId,
                                          @Valid @RequestBody AddressDTO request) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("更新收货地址, 用户 ID: {}, 收货地址 ID: {}, 请求: {}", currentUserId, addressId, request);
        AddressVO vo = addressService.updateAddress(currentUserId, addressId, request);
        return Result.success(vo);
    }

    /**
     * 删除当前用户名下的指定地址。
     */
    @DeleteMapping("/{addressId}")
    public Result<String> deleteAddress(@PathVariable Long addressId) {
        Long currentUserId = BaseContext.getCurrentId();
        log.info("删除收货地址, 用户 ID: {}, 收货地址 ID: {}", currentUserId, addressId);
        addressService.deleteAddress(currentUserId, addressId);
        return Result.success("删除成功");
    }

    /**
     * 将指定地址设为当前用户默认地址。
     */
    @PutMapping("/{addressId}/default")
    public Result<String> setDefaultAddress(@PathVariable Long addressId) {
        Long currentUserId = BaseContext.getCurrentId();
        addressService.setDefaultAddress(currentUserId, addressId);
        return Result.success("设置默认地址成功");
    }
    /**
     * 按地址 ID 查询地址详情（地址必须属于当前用户）。
     */
    @GetMapping("/{addressId}")
    public Result<AddressVO> getAddress(@PathVariable Long addressId) {
        Long currentUserId = BaseContext.getCurrentId();
        AddressVO vo = addressService.getAddressById(currentUserId, addressId);
        return Result.success(vo);
    }

}

