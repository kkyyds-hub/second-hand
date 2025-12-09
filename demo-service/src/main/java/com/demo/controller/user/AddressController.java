package com.demo.controller.user;

import com.demo.context.BaseContext;
import com.demo.result.Result;
import com.demo.service.AddressService;
import com.demo.vo.address.AddressVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/address")
@Slf4j
public class AddressController {

    @Autowired
    private AddressService addressService;
    @GetMapping
    public Result<List<AddressVO>> getAddress(){
        log.info("获取用户地址");
        Long currentUserId = BaseContext.getCurrentId();
        List<AddressVO> addressVOList = addressService.getAddress(currentUserId);

        return Result.success();
    }
}
