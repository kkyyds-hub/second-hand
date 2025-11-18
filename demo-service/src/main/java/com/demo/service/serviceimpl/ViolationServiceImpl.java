package com.demo.service.serviceimpl;


import com.demo.constant.MessageConstant;
import com.demo.dto.base.BanRequest;
import com.demo.entity.User;
import com.demo.entity.UserViolation;
import com.demo.mapper.ViolationMapper;
import com.demo.service.ViolationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
@Slf4j
public class ViolationServiceImpl implements ViolationService {
    @Autowired
    private ViolationMapper violationMapper;
    @Override
    public void unbanUser(Long userId) {
        User user  =violationMapper.SelectById(userId);
        if (user == null){
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        if ("active".equals(user.getStatus())) {
            throw new RuntimeException(MessageConstant.ALREADY_EXISTS);
        }
        user.setStatus("active");
        user.setUpdateTime(LocalDateTime.now());
        violationMapper.update(user);
    }

    @Override
    public void banUser(Long userId, String reason ) {
        User user  =violationMapper.SelectById(userId);
        if (user == null){
            throw new RuntimeException(MessageConstant.ID_NOT_FOUND);
        }
        if ("banned".equals(user.getStatus())) {
            throw new RuntimeException(MessageConstant.BANNED_EXISTS);
        }
        user.setStatus("active");
        user.setUpdateTime(LocalDateTime.now());
        violationMapper.update(user);
        UserViolation violation = new UserViolation();
        BanRequest request = new BanRequest();
        violation.setUserId(userId);
        violation.setViolationType(request.getViolationType() != null ?
                request.getViolationType() : "其他违规");
        violation.setDescription(reason);
        violation.setPunish("账号封禁");
        violation.setCredit(-10); // 信用分扣减
        violation.setCreateTime(LocalDateTime.now());

        violationMapper.insert(violation);

        log.info("用户封禁成功并记录违规: userId={}, reason={}", userId, reason);
    }
}
