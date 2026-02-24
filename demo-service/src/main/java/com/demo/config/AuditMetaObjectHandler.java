package com.demo.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 全局审计字段自动填充处理器。
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // 统一服务端时间口径：插入时覆盖 createTime/updateTime
        setFieldValByName("createTime", now, metaObject);
        setFieldValByName("updateTime", now, metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 统一服务端时间口径：更新时覆盖 updateTime
        setFieldValByName("updateTime", LocalDateTime.now(), metaObject);
    }
}
