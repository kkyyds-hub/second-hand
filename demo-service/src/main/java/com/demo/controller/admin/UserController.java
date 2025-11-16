package com.demo.controller.admin;

import com.demo.dto.user.UserQueryDTO;
import com.demo.result.PageResult;
import com.demo.result.Result;
import com.demo.service.UserService;
import com.demo.vo.UserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@RestController
@Api(tags = "用户管理")
@RequestMapping("/admin/user")
@Slf4j
public class UserController {

    @Autowired
    private UserService userService;
    @GetMapping
    @ApiOperation("用户分页查询")
    public Result<PageResult<UserVO>> getUsers(@Valid UserQueryDTO queryDTO) {
        log.info("用户分页查询: page={}, size={}", queryDTO.getPage(), queryDTO.getSize());
        PageResult<UserVO> pageResult = userService.getUserPage(queryDTO);
        return Result.success(pageResult);
    }

}
