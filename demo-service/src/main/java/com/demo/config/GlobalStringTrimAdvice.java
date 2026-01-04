package com.demo.config;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * 全局字符串入参归一化：
 * - 自动 trim
 * - trim 后如果为空串 -> 转成 null
 *
 * 生效范围：@RequestParam / @ModelAttribute（也就是 URL 查询参数、表单参数绑定）
 * 注意：不影响 @RequestBody JSON（JSON 归 Jackson 处理）
 */
@ControllerAdvice
public class GlobalStringTrimAdvice {

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        // true：trim 后空串转 null
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }
}
