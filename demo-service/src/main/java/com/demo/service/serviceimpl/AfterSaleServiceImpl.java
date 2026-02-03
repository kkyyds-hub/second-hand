package com.demo.service.serviceimpl;

import com.demo.dto.aftersale.ArbitrateRequest;
import com.demo.dto.aftersale.CreateAfterSaleRequest;
import com.demo.dto.aftersale.DisputeRequest;
import com.demo.dto.aftersale.SellerDecisionRequest;
import com.demo.entity.AfterSale;
import com.demo.entity.AfterSaleEvidence;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.AfterSaleMapper;
import com.demo.mapper.OrderMapper;
import com.demo.service.AfterSaleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Day13 Step5 - 售后服务实现
 */
@Service
@Slf4j
public class AfterSaleServiceImpl implements AfterSaleService {

    @Autowired
    private AfterSaleMapper afterSaleMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createAfterSale(Long currentUserId, CreateAfterSaleRequest request) {
        Long orderId = request.getOrderId();

        // 1. 订单必须存在
        Order order = orderMapper.selectOrderBasicById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 当前用户必须是买家
        if (!Objects.equals(currentUserId, order.getBuyerId())) {
            throw new BusinessException("只有买家本人可以申请售后");
        }

        // 3. 订单状态必须为 completed
        OrderStatus status = OrderStatus.fromDbValue(order.getStatus());
        if (status != OrderStatus.COMPLETED) {
            throw new BusinessException("订单未完成，无法申请售后");
        }

        // 4. complete_time 距当前时间不超过 7 天
        LocalDateTime completeTime = order.getCompleteTime();
        if (completeTime == null) {
            throw new BusinessException("订单完成时间异常");
        }
        long daysSinceComplete = ChronoUnit.DAYS.between(completeTime, LocalDateTime.now());
        if (daysSinceComplete > 7) {
            throw new BusinessException("已超过确认收货7天，无法申请售后");
        }

        // 5. 同一订单仅允许存在 1 条售后（幂等检查）
        AfterSale existing = afterSaleMapper.selectByOrderId(orderId);
        if (existing != null) {
            throw new BusinessException("该订单已存在售后申请");
        }

        // 6. 组装售后记录
        AfterSale afterSale = new AfterSale();
        afterSale.setOrderId(orderId);
        afterSale.setBuyerId(currentUserId);
        afterSale.setSellerId(order.getSellerId());
        afterSale.setReason(request.getReason());
        afterSale.setStatus("APPLIED");

        // 7. 插入售后记录
        try {
            afterSaleMapper.insertAfterSale(afterSale);
        } catch (DuplicateKeyException e) {
            log.warn("售后唯一键冲突：orderId={}", orderId, e);
            throw new BusinessException("该订单已存在售后申请");
        }

        // 8. 插入凭证（最多 3 张）
        if (request.getEvidenceImages() != null && !request.getEvidenceImages().isEmpty()) {
            List<AfterSaleEvidence> evidences = new ArrayList<>();
            int sort = 1;
            for (String imageUrl : request.getEvidenceImages()) {
                if (imageUrl != null && !imageUrl.trim().isEmpty()) {
                    AfterSaleEvidence evidence = new AfterSaleEvidence();
                    evidence.setAfterSaleId(afterSale.getId());
                    evidence.setImageUrl(imageUrl.trim());
                    evidence.setSort(sort++);
                    evidences.add(evidence);
                }
            }
            if (!evidences.isEmpty()) {
                afterSaleMapper.batchInsertEvidences(evidences);
            }
        }

        log.info("售后申请成功：afterSaleId={}, orderId={}, buyerId={}",
                afterSale.getId(), orderId, currentUserId);
        return afterSale.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String sellerDecision(Long afterSaleId, Long currentUserId, SellerDecisionRequest request) {
        // 1. 售后记录必须存在
        AfterSale afterSale = afterSaleMapper.selectById(afterSaleId);
        if (afterSale == null) {
            throw new BusinessException("售后记录不存在");
        }

        // 2. 当前用户必须是卖家
        if (!Objects.equals(currentUserId, afterSale.getSellerId())) {
            throw new BusinessException("只有卖家本人可以处理");
        }

        // 3. 状态必须为 APPLIED
        if (!"APPLIED".equals(afterSale.getStatus())) {
            throw new BusinessException("售后状态不允许处理");
        }

        // 4. 更新状态和备注
        String newStatus = request.getApproved() ? "SELLER_APPROVED" : "SELLER_REJECTED";
        String remark = request.getRemark() != null ? request.getRemark().trim() : "";

        int rows = afterSaleMapper.updateSellerDecision(afterSaleId, newStatus, remark);
        if (rows != 1) {
            throw new BusinessException("处理失败");
        }

        log.info("卖家处理售后：afterSaleId={}, sellerId={}, approved={}",
                afterSaleId, currentUserId, request.getApproved());

        return request.getApproved() ? "已同意退货退款" : "已拒绝退货退款";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String submitDispute(Long afterSaleId, Long currentUserId, DisputeRequest request) {
        // 1. 售后记录必须存在
        AfterSale afterSale = afterSaleMapper.selectById(afterSaleId);
        if (afterSale == null) {
            throw new BusinessException("售后记录不存在");
        }

        // 2. 当前用户必须是买家
        if (!Objects.equals(currentUserId, afterSale.getBuyerId())) {
            throw new BusinessException("只有买家本人可以提交纠纷");
        }

        // 3. 状态必须为 SELLER_REJECTED
        if (!"SELLER_REJECTED".equals(afterSale.getStatus())) {
            throw new BusinessException("只能对卖家拒绝的售后提交纠纷");
        }

        // 4. 更新状态为 DISPUTED
        int rows = afterSaleMapper.updateToDisputed(afterSaleId);
        if (rows != 1) {
            throw new BusinessException("提交纠纷失败");
        }

        log.info("买家提交纠纷：afterSaleId={}, buyerId={}, content={}",
                afterSaleId, currentUserId, request.getContent());

        return "纠纷已提交，等待平台介入";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String arbitrate(Long afterSaleId, ArbitrateRequest request) {
        // 1. 售后记录必须存在
        AfterSale afterSale = afterSaleMapper.selectById(afterSaleId);
        if (afterSale == null) {
            throw new BusinessException("售后记录不存在");
        }

        // 2. 状态必须为 DISPUTED
        if (!"DISPUTED".equals(afterSale.getStatus())) {
            throw new BusinessException("售后状态不允许裁决");
        }

        // 3. 更新状态和备注
        String newStatus = request.getApproved() ? "PLATFORM_APPROVED" : "PLATFORM_REJECTED";
        String remark = request.getRemark() != null ? request.getRemark().trim() : "";

        int rows = afterSaleMapper.updatePlatformArbitrate(afterSaleId, newStatus, remark);
        if (rows != 1) {
            throw new BusinessException("裁决失败");
        }

        log.info("平台裁决售后：afterSaleId={}, approved={}", afterSaleId, request.getApproved());

        return request.getApproved() ? "裁决通过，支持退货退款" : "裁决驳回";
    }
}
