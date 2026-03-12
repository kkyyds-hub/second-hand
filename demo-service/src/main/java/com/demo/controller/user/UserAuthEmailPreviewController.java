package com.demo.controller.user;

import com.demo.exception.BusinessException;
import com.demo.mail.EmailPreviewStore;
import com.demo.result.Result;
import com.demo.vo.EmailPreviewVO;
import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.Email;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * 开发环境邮件预览接口。
 */
@Slf4j
@Validated
@RestController
@Profile("dev")
@RequestMapping("/user/auth/email-preview")
@Api(tags = "用户认证-开发邮件预览")
public class UserAuthEmailPreviewController {

    private final EmailPreviewStore emailPreviewStore;

    public UserAuthEmailPreviewController(EmailPreviewStore emailPreviewStore) {
        this.emailPreviewStore = emailPreviewStore;
    }

    @GetMapping("/latest")
    public Result<EmailPreviewVO> getLatest(@RequestParam("email")
                                            @NotBlank(message = "邮箱不能为空")
                                            @Email(message = "邮箱格式不正确") String email) {
        EmailPreviewVO preview = emailPreviewStore.getLatest(email);
        if (preview == null) {
            throw new BusinessException("未找到该邮箱的激活邮件预览");
        }
        log.info("查询最新激活邮件预览: email={}", maskEmail(email));
        return Result.success(preview);
    }

    @GetMapping("/recent")
    public Result<List<EmailPreviewVO>> listRecent(@RequestParam(value = "limit", defaultValue = "10")
                                                   @Min(value = 1, message = "limit 不能小于1")
                                                   @Max(value = 20, message = "limit 不能大于20") Integer limit) {
        log.info("查询最近激活邮件预览: limit={}", limit);
        return Result.success(emailPreviewStore.listRecent(limit));
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return "EMPTY";
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 1) {
            return "***@" + (at >= 0 ? trimmed.substring(at + 1) : "***");
        }
        return trimmed.substring(0, 1) + "***@" + trimmed.substring(at + 1);
    }
}
