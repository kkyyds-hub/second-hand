package com.demo.service;

import com.demo.dto.user.UserQueryDTO;
import com.demo.result.PageResult;
import com.demo.vo.UserVO;

import java.util.List;

public interface UserService {
    PageResult<UserVO> getUserPage(UserQueryDTO queryDTO);


}
