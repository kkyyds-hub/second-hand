package com.demo.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Day13 Step3 - 站内消息（MongoDB）
 * Collection: order_messages
 * 索引见 Day13 文档 2.6.3
 */
@Data
@Document(collection = "order_messages")
@CompoundIndexes({
        @CompoundIndex(name = "idx_order_time", def = "{'orderId': 1, 'createTime': -1}"),
        @CompoundIndex(name = "uniq_order_clientMsg", def = "{'orderId': 1, 'fromUserId': 1, 'clientMsgId': 1}", unique = true),
        @CompoundIndex(name = "idx_to_read", def = "{'toUserId': 1, 'read': 1, 'createTime': -1}")
})
public class Message {

    @Id
    private String id; // MongoDB ObjectId

    private Long orderId;
    private Long fromUserId;
    private Long toUserId;

    /**
     * 消息内容（Day13 仅支持文本）
     */
    private String content;

    /**
     * 是否已读：false=未读，true=已读
     */
    @Indexed
    private Boolean read = false;

    /**
     * 客户端生成的幂等键（UUID/雪花ID）
     */
    private String clientMsgId;

    private LocalDateTime createTime;
}
