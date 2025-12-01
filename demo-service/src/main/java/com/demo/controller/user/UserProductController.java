package com.demo.controller.user;

import com.demo.dto.user.ProductDTO;
import com.demo.dto.user.UserProductQueryDTO;
import com.demo.entity.Product;
import com.demo.result.Result;
import com.demo.service.ProductService;
import com.demo.service.UserService;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RestController
@RequestMapping("/user/products")
@Validated
@Slf4j
public class UserProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    public Result<PageInfo<Product>> getProducts(@Validated UserProductQueryDTO queryDTO) {
        log.info("获取用户商品列表");
        PageInfo<Product> pageInfo = productService.getUserProducts(queryDTO);

        return Result.success(pageInfo);
    }


}
