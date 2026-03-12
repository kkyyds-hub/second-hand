package com.demo.vo.payment;

import lombok.Data;

/**
 * Mock 支付演示结果。
 */
@Data
public class MockPaymentSimulationVO {

    /** 订单 ID。 */
    private Long orderId;
    /** 订单号。 */
    private String orderNo;
    /** 演示场景：SUCCESS / FAIL / REPEAT。 */
    private String scenario;
    /** 回调通道，当前固定为 mock。 */
    private String channel;
    /** 回调前订单状态。 */
    private String beforeStatus;
    /** 回调后订单状态。 */
    private String afterStatus;
    /** 实际发送的回调次数。 */
    private Integer callbackCount;
    /** 第一次回调的业务状态。 */
    private String firstStatus;
    /** 第一次回调的交易流水号。 */
    private String firstTradeNo;
    /** 第一次回调的处理结果。 */
    private String firstResult;
    /** 第二次回调的业务状态（仅 REPEAT 场景有值）。 */
    private String secondStatus;
    /** 第二次回调的交易流水号（仅 REPEAT 场景有值）。 */
    private String secondTradeNo;
    /** 第二次回调的处理结果（仅 REPEAT 场景有值）。 */
    private String secondResult;
    /** 最终对外展示结果。 */
    private String finalResult;
}
