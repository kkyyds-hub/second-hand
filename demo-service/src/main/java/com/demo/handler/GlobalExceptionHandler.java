package com.demo.handler;

import com.demo.constant.MessageConstant;
import com.demo.exception.BaseException;
import com.demo.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import javax.validation.ConstraintViolationException;
import java.util.Optional;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    /**
     * 处理 @RequestBody 校验失败（例如 RejectProductRequest）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        return Result.error(msg);
    }

    /**
     * 处理 @RequestParam / @ModelAttribute 绑定校验失败
     */
    @ExceptionHandler(BindException.class)
    public Result handleBindException(BindException ex) {
        String msg = Optional.ofNullable(ex.getBindingResult().getFieldError())
                .map(FieldError::getDefaultMessage)
                .orElse("参数校验失败");
        return Result.error(msg);
    }

    /**
     * 处理方法参数上的约束校验失败（@RequestParam + @Validated 这类）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result handleConstraintViolation(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("参数校验失败");
        return Result.error(msg);
    }

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
