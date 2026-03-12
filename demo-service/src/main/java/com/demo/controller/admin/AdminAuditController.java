package com.demo.controller.admin;

import com.demo.dto.admin.AdminAuditQueryDTO;
import com.demo.result.Result;
import com.demo.service.AdminAuditService;
import com.demo.vo.admin.AdminAuditOverviewVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端纠纷与违规聚合接口。
 */
@RestController
@RequestMapping("/admin/audit")
@Api(tags = "管理端纠纷与违规")
@Slf4j
public class AdminAuditController {

    @Autowired
    private AdminAuditService adminAuditService;

    /**
     * 查询纠纷与违规页总览。
     */
    @GetMapping("/overview")
    @ApiOperation("查询纠纷与违规页总览")
    public Result<AdminAuditOverviewVO> overview(AdminAuditQueryDTO queryDTO) {
        log.info("查询纠纷与违规页总览：keyword={}, type={}, status={}, riskLevel={}",
                queryDTO.getKeyword(), queryDTO.getType(), queryDTO.getStatus(), queryDTO.getRiskLevel());
        return Result.success(adminAuditService.getOverview(queryDTO));
    }
}
