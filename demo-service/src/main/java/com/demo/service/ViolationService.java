package com.demo.service;

public interface ViolationService {

    void unbanUser(Long userId);

    void banUser(Long userId, String reason);
}
