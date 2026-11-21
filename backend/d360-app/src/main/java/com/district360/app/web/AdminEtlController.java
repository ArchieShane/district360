package com.district360.app.web;

import com.district360.app.config.AdminProperties;
import com.district360.app.etl.EtlRunResult;
import com.district360.app.etl.EtlService;
import com.district360.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** 手动触发抽取：POST /api/v1/admin/etl/{makani|liveability|adrec|all}，请求头 X-Admin-Token。 */
@RestController
@RequestMapping("/api/v1/admin/etl")
public class AdminEtlController {

    private final EtlService etl;
    private final AdminProperties admin;

    public AdminEtlController(EtlService etl, AdminProperties admin) {
        this.etl = etl;
        this.admin = admin;
    }

    @PostMapping("/{source}")
    public ResponseEntity<ApiResponse<List<EtlRunResult>>> run(@PathVariable String source,
                                                               @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!StringUtils.hasText(admin.token())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(403, "未配置 d360.admin.token，手动抽取接口已禁用"));
        }
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), admin.token().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "X-Admin-Token 不正确"));
        }
        List<EtlRunResult> results = "all".equals(source) ? etl.runAll() : List.of(etl.run(source));
        return ResponseEntity.ok(ApiResponse.ok(results));
    }
}
