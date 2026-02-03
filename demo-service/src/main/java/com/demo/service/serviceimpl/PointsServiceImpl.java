package com.demo.service.serviceimpl;

import com.demo.entity.PointsLedger;
import com.demo.mapper.PointsMapper;
import com.demo.result.PageResult;
import com.demo.service.PointsService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Day13 Step8 - 积分服务实现
 */
@Service
@Slf4j
public class PointsServiceImpl implements PointsService {

    @Autowired
    private PointsMapper pointsMapper;

    @Value("${points.order-complete:1}")
    private int pointsPerOrder;

    @Override
    public Integer getTotalPoints(Long userId) {
        Integer total = pointsMapper.sumPointsByUserId(userId);
        return total != null ? total : 0;
    }

    @Override
    public PageResult<PointsLedger> listPoints(Long userId, Integer page, Integer pageSize) {
        PageHelper.startPage(page, pageSize);
        List<PointsLedger> list = pointsMapper.listPointsByUserId(userId);
        PageInfo<PointsLedger> pageInfo = new PageInfo<>(list);
        return new PageResult<>(pageInfo.getList(), pageInfo.getTotal(), page, pageSize);
    }

    @Override
    public void grantPointsForOrderComplete(Long orderId, Long buyerId, Long sellerId) {
        // 给买家加积分
        PointsLedger buyerLedger = new PointsLedger();
        buyerLedger.setUserId(buyerId);
        buyerLedger.setBizType("ORDER_COMPLETED");
        buyerLedger.setBizId(orderId);
        buyerLedger.setPoints(pointsPerOrder);

        try {
            pointsMapper.insertPoints(buyerLedger);
            log.info("积分发放成功：userId={}, orderId={}, points={}", buyerId, orderId, pointsPerOrder);
        } catch (DuplicateKeyException e) {
            log.warn("积分重复发放（幂等）：userId={}, orderId={}", buyerId, orderId);
        }

        // 给卖家加积分
        PointsLedger sellerLedger = new PointsLedger();
        sellerLedger.setUserId(sellerId);
        sellerLedger.setBizType("ORDER_COMPLETED");
        sellerLedger.setBizId(orderId);
        sellerLedger.setPoints(pointsPerOrder);

        try {
            pointsMapper.insertPoints(sellerLedger);
            log.info("积分发放成功：userId={}, orderId={}, points={}", sellerId, orderId, pointsPerOrder);
        } catch (DuplicateKeyException e) {
            log.warn("积分重复发放（幂等）：userId={}, orderId={}", sellerId, orderId);
        }
    }
}
