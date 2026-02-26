package com.demo.service.serviceimpl;

import com.demo.constant.MessageConstant;
import com.demo.dto.message.MessageDTO;
import com.demo.dto.message.SendMessageRequest;
import com.demo.entity.Message;
import com.demo.entity.Order;
import com.demo.enumeration.OrderStatus;
import com.demo.exception.BusinessException;
import com.demo.mapper.OrderMapper;
import com.demo.repository.MessageRepository;
import com.demo.result.PageResult;
import com.demo.security.InputSecurityGuard;
import com.demo.service.MessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Day13 Step3 - 站内消息服务实现。
 * 负责订单会话消息发送、历史查询、未读统计和已读回执。
 */
@Service
@Slf4j
public class MessageServiceImpl implements MessageService {

    /**
     * Day16 系统通知固定会话槽位（非订单会话）。
     */
    private static final Long SYSTEM_NOTICE_ORDER_ID = 0L;

    /**
     * Day16 系统通知固定发送人。
     * 仅用于查询条件收敛，避免把用户伪造的 orderId=0 消息混入系统通知列表。
     */
    private static final Long SYSTEM_NOTICE_FROM_USER_ID = 0L;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 发送站内消息。
     * 包含订单归属校验、收件人校验、发送频控和 clientMsgId 幂等处理。
     */
    @Override
    public MessageDTO sendMessage(Long orderId, Long currentUserId, SendMessageRequest request) {
        // Day18 P3-S2：站内信文本会直接展示给对端用户，必须先做输入安全守卫。
        // safeClientMsgId 也做统一规范化，避免脏值影响幂等查询条件。
        String safeClientMsgId = InputSecurityGuard.normalizePlainText(request.getClientMsgId(), "客户端消息ID", 64, true);
        String safeContent = InputSecurityGuard.normalizePlainText(request.getContent(), "消息内容", 500, true);

        // 1. 订单必须存在
        Order order = orderMapper.selectOrderBasicById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 当前用户必须是订单买家或卖家
        boolean isBuyer = Objects.equals(currentUserId, order.getBuyerId());
        boolean isSeller = Objects.equals(currentUserId, order.getSellerId());
        if (!isBuyer && !isSeller) {
            throw new BusinessException(MessageConstant.MESSAGE_NO_PERMISSION);
        }

        // 2.1 toUserId 必须是订单另一方
        Long expectedToUserId = isBuyer ? order.getSellerId() : order.getBuyerId();
        if (!Objects.equals(request.getToUserId(), expectedToUserId)) {
            throw new BusinessException(MessageConstant.MESSAGE_NO_PERMISSION);
        }

        // 3. 订单状态不得为 cancelled
        OrderStatus status = OrderStatus.fromDbValue(order.getStatus());
        if (status == OrderStatus.CANCELLED) {
            throw new BusinessException("订单已取消，无法发送消息");
        }

        // 4. 频控：单用户单会话 1 秒内最多 3 条
        LocalDateTime oneSecondAgo = LocalDateTime.now().minusSeconds(1);
        Query frequencyQuery = new Query();
        frequencyQuery.addCriteria(
                Criteria.where("orderId").is(orderId)
                        .and("fromUserId").is(currentUserId)
                        .and("createTime").gte(oneSecondAgo)
        );
        long recentCount = mongoTemplate.count(frequencyQuery, Message.class);
        if (recentCount >= 3) {
            throw new BusinessException("发送过于频繁，请稍后再试");
        }

        // 5. 组装消息
        Message message = new Message();
        message.setOrderId(orderId);
        message.setFromUserId(currentUserId);
        message.setToUserId(request.getToUserId());
        message.setContent(safeContent);
        message.setClientMsgId(safeClientMsgId);
        message.setRead(false);
        message.setCreateTime(LocalDateTime.now());

        // 6. 幂等：clientMsgId 唯一
        try {
            messageRepository.save(message);
        } catch (DuplicateKeyException e) {
            log.info("幂等命中：action=sendMessage, idemKey=clientMsgId:{}, detail=orderId={},fromUserId={}",
                    safeClientMsgId, orderId, currentUserId);
            Query query = new Query();
            query.addCriteria(
                    Criteria.where("orderId").is(orderId)
                            .and("fromUserId").is(currentUserId)
                            .and("clientMsgId").is(safeClientMsgId)
            );
            Message existing = mongoTemplate.findOne(query, Message.class);
            if (existing != null) {
                return convertToDTO(existing);
            }
            throw new BusinessException("消息发送失败");
        }

        log.info("消息发送成功: messageId={}, orderId={}, from={}, to={}",
                message.getId(), orderId, currentUserId, request.getToUserId());

        return convertToDTO(message);
    }

    /**
     * 分页查询订单会话消息（按 createTime 升序）。
     */
    @Override
    public PageResult<MessageDTO> listMessages(Long orderId, Long currentUserId, Integer page, Integer pageSize) {
        // 1. 订单必须存在
        Order order = orderMapper.selectOrderBasicById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 当前用户必须是订单买家或卖家
        boolean isBuyer = Objects.equals(currentUserId, order.getBuyerId());
        boolean isSeller = Objects.equals(currentUserId, order.getSellerId());
        if (!isBuyer && !isSeller) {
            throw new BusinessException(MessageConstant.MESSAGE_NO_PERMISSION);
        }

        // 3. 分页查询（按 createTime 升序）
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<Message> messagePage = messageRepository.findByOrderIdOrderByCreateTimeAsc(orderId, pageable);

        List<MessageDTO> dtoList = messagePage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return new PageResult<>(dtoList, messagePage.getTotalElements(), page, pageSize);
    }

    /**
     * 查询当前用户未读消息总数。
     */
    @Override
    public Long getUnreadCount(Long currentUserId) {
        return messageRepository.countByToUserIdAndRead(currentUserId, false);
    }

    /**
     * 将当前用户在指定订单会话中的未读消息批量标记为已读。
     */
    @Override
    public String markAsRead(Long orderId, Long currentUserId) {
        // 1. 订单必须存在
        Order order = orderMapper.selectOrderBasicById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 当前用户必须是订单买家或卖家
        boolean isBuyer = Objects.equals(currentUserId, order.getBuyerId());
        boolean isSeller = Objects.equals(currentUserId, order.getSellerId());
        if (!isBuyer && !isSeller) {
            throw new BusinessException(MessageConstant.MESSAGE_NO_PERMISSION);
        }

        // 3. 批量更新为已读
        Query query = new Query();
        query.addCriteria(
                Criteria.where("orderId").is(orderId)
                        .and("toUserId").is(currentUserId)
                        .and("read").is(false)
        );
        Update update = new Update().set("read", true);
        long count = mongoTemplate.updateMulti(query, update, Message.class).getModifiedCount();

        log.info("标记订单会话已读: orderId={}, userId={}, count={}", orderId, currentUserId, count);
        return "已标记" + count + "条消息为已读";
    }

    /**
     * Day16 Step6 - 分页查询系统通知。
     *
     * 核心逻辑：
     * 1) 不查订单表，不走“买卖家归属”模型；
     * 2) 按 orderId=0 + fromUserId=0 + toUserId=currentUserId 收敛查询范围；
     * 3) 按 createTime 倒序返回，保证用户先看到最新系统通知。
     */
    @Override
    public PageResult<MessageDTO> listSystemNotices(Long currentUserId, Integer page, Integer pageSize) {
        validateCurrentUser(currentUserId);
        int safePage = (page == null || page < 1) ? 1 : page;
        int safePageSize = (pageSize == null || pageSize < 1) ? 20 : pageSize;

        Query query = new Query();
        query.addCriteria(buildSystemNoticeCriteria(currentUserId));

        Pageable pageable = PageRequest.of(safePage - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createTime"));
        query.with(pageable);

        List<MessageDTO> dtoList = mongoTemplate.find(query, Message.class).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        Query countQuery = new Query();
        countQuery.addCriteria(buildSystemNoticeCriteria(currentUserId));
        long total = mongoTemplate.count(countQuery, Message.class);

        return new PageResult<>(dtoList, total, safePage, safePageSize);
    }

    /**
     * Day16 Step6 - 查询系统通知详情。
     *
     * 权限模型：
     * - 仅允许读取发送给当前用户的系统通知；
     * - 若 messageId 不存在或不属于当前用户，统一返回“系统通知不存在”。
     */
    @Override
    public MessageDTO getSystemNoticeDetail(String messageId, Long currentUserId) {
        validateCurrentUser(currentUserId);
        if (messageId == null || messageId.trim().isEmpty()) {
            throw new BusinessException("messageId 不能为空");
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(messageId.trim()));
        query.addCriteria(buildSystemNoticeCriteria(currentUserId));

        Message message = mongoTemplate.findOne(query, Message.class);
        if (message == null) {
            throw new BusinessException("系统通知不存在");
        }
        return convertToDTO(message);
    }

    /**
     * Day16 Step6 - 批量标记系统通知为已读。
     * 仅操作当前用户的系统通知槽位消息，不影响订单会话消息。
     */
    @Override
    public String markSystemNoticesAsRead(Long currentUserId) {
        validateCurrentUser(currentUserId);
        Query query = new Query();
        query.addCriteria(
                buildSystemNoticeCriteria(currentUserId)
                        .and("read").is(false)
        );

        Update update = new Update().set("read", true);
        long count = mongoTemplate.updateMulti(query, update, Message.class).getModifiedCount();

        log.info("标记系统通知已读: userId={}, count={}", currentUserId, count);
        return "已标记" + count + "条系统通知为已读";
    }

    /**
     * 系统通知基础条件。
     * 统一约束三元组：orderId=0 + fromUserId=0 + toUserId=currentUserId。
     */
    private Criteria buildSystemNoticeCriteria(Long currentUserId) {
        return Criteria.where("orderId").is(SYSTEM_NOTICE_ORDER_ID)
                .and("fromUserId").is(SYSTEM_NOTICE_FROM_USER_ID)
                .and("toUserId").is(currentUserId);
    }

    /**
     * 统一登录态保护，避免空用户造成越权查询。
     */
    private void validateCurrentUser(Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(MessageConstant.USER_NOT_LOGIN);
        }
    }

    /**
     * 实体转 DTO，隔离持久化对象与接口返回对象。
     */
    private MessageDTO convertToDTO(Message message) {
        MessageDTO dto = new MessageDTO();
        BeanUtils.copyProperties(message, dto);
        return dto;
    }
}
