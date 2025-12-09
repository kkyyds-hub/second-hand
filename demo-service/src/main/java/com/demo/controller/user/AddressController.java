package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.result.Result;
import com.demo.service.AddressService;
import com.demo.vo.address.AddressVO;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
