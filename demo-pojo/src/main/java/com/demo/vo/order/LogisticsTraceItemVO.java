package com.demo.vo.order;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流轨迹节点
 */
@Data
public class LogisticsTraceItemVO {

    /** 节点时间 */
    private LocalDateTime time;

    /** 节点地点 */
    private String location;

    /** 节点状态描述（如：已揽收、运输中、已签收 等） */
    private String status;
}
