package com.demo.handler;

import com.demo.constant.MessageConstant;
import com.demo.exception.BaseException;
import com.demo.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    /**
     * ✅ 添加通用异常处理
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception ex) {
        log.error("❌ 捕获到未处理异常: {}", ex.getMessage(), ex);
        ex.printStackTrace(); // 关键：打印完整堆栈

        return Result.error("服务器错误: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }
    /**
     * 捕获业务异常
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex){
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }


    @ExceptionHandler
    public Result exceptiobHandler(SQLIntegrityConstraintViolationException ex){
        log.error("异常信息：{}", ex.getMessage());
        String message = ex.getMessage();
        if (message.contains("Duplicate entry")) {
            String[] split = message.split(" ");
            String msg = split[2] + MessageConstant.ALREADY_EXISTS;
            return Result.error(msg);
        }else {
            return Result.error(MessageConstant.UNKNOWN_ERROR);
        }

    }

}
