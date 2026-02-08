package com.demo.mapper;

import com.demo.entity.MqConsumeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Day14 - MQ 消费幂等日志 Mapper
 *
 * 作用：记录“某个消费者是否已经处理过某条事件”
 */
@Mapper
public interface MqConsumeLogMapper {

    /**
     * 根据 consumer + eventId 查询
     *
     * @param consumer 消费者标识
     * @param eventId  事件ID
     * @return 已存在的消费记录（null 表示未消费）
     */
    MqConsumeLog selectByConsumerAndEventId(@Param("consumer") String consumer,
                                            @Param("eventId") String eventId);

    /**
     * 插入一条消费日志
     *
     * @param log 日志实体
     * @return 插入行数
     */
    int insert(MqConsumeLog log);

    /**
     * 更新消费状态（可选）
     *
     * @param id     主键
     * @param status 新状态
     * @return 更新行数
     */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status);
}
