package com.district360.app.web;

import com.district360.app.service.DistrictService;
import com.district360.app.web.dto.DistrictListItem;
import com.district360.app.web.dto.DistrictOverview;
import com.district360.app.web.dto.MetaInfo;
import com.district360.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class DistrictController {

    private final DistrictService service;

    public DistrictController(DistrictService service) {
        this.service = service;
    }

    /** 区列表：首页介绍卡、左侧列表、地图悬停。 */
    @GetMapping("/districts")
    public ApiResponse<List<DistrictListItem>> districts() {
        return ApiResponse.ok(service.listDistricts());
    }

    /** 右侧面板：标题 + 宜居、土地与建设、房地产、公共设施。 */
    @GetMapping("/districts/{code}/overview")
    public ApiResponse<DistrictOverview> overview(@PathVariable String code) {
        return ApiResponse.ok(service.overview(code));
    }

    /** 区数量、各源数据日期和最近一次抽取状态。 */
    @GetMapping("/meta")
    public ApiResponse<MetaInfo> meta() {
        return ApiResponse.ok(service.meta());
    }
}
