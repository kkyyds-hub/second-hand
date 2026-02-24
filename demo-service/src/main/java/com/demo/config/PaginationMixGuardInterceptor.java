package com.demo.config;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.github.pagehelper.page.PageMethod;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Map;
import java.util.Properties;

/**
 * 防止同一查询链路同时使用 PageHelper 和 MyBatis-Plus 分页。
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                org.apache.ibatis.cache.CacheKey.class, org.apache.ibatis.mapping.BoundSql.class
        })
})
public class PaginationMixGuardInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object parameterObject = invocation.getArgs()[1];
        if (isPageHelperStarted() && containsMybatisPlusPage(parameterObject)) {
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            throw new IllegalStateException(
                    "Detected mixed pagination strategy in one query chain: PageHelper + MyBatis-Plus. " +
                            "Please keep a single pagination plugin per request. mapperId=" +
                            mappedStatement.getId()
            );
        }
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }

    private boolean isPageHelperStarted() {
        try {
            return PageMethod.getLocalPage() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean containsMybatisPlusPage(Object parameterObject) {
        if (parameterObject == null) {
            return false;
        }
        if (parameterObject instanceof IPage) {
            return true;
        }
        if (!(parameterObject instanceof Map<?, ?> parameterMap)) {
            return false;
        }
        for (Object value : parameterMap.values()) {
            if (value instanceof IPage) {
                return true;
            }
        }
        return false;
    }
}
