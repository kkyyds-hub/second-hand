package com.demo.repository;

import com.demo.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Day13 Step3 - 站内消息 MongoDB Repository
 */
@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    /**
     * 根据 orderId 分页查询消息（按 createTime 升序）
     */
    Page<Message> findByOrderIdOrderByCreateTimeAsc(Long orderId, Pageable pageable);

    /**
     * 统计未读消息数
     */
    long countByToUserIdAndRead(Long toUserId, Boolean read);

    /**
     * 查询订单会话中当前用户在 1 秒内发送的消息数（频控）
     * 需要在 Service 层用 MongoTemplate 实现复杂查询
     */
}
