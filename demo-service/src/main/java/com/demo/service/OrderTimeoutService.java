package com.demo.service;


import java.time.LocalDateTime;

public interface OrderTimeoutService {

    boolean closeTimeoutOrderAndRelease(Long orderId, LocalDateTime deadline);
}
