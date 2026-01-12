package com.demo.service;

import com.demo.dto.user.SellerSummaryDTO;

public interface SellerService {
    SellerSummaryDTO getSummary(Long sellerId);
}