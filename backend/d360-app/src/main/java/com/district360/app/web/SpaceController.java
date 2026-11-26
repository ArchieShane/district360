package com.district360.app.web;

import com.district360.app.space.NodeDetail;
import com.district360.app.space.SpaceLayer;
import com.district360.app.space.SpaceLevel;
import com.district360.app.space.SpaceService;
import com.district360.app.web.dto.PageResult;
import com.district360.common.api.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * District 360 V1.0 空间下钻接口：酋长国 → 市政 → 区域 → 社区 → 地块 → 建筑 → 楼层 → 单元。只读主题库 theme.sp_*。
 * 层级编码：酋长国 AD；市政 ADM / AAM / WRM；区域 ADM-93；社区 AAM-79-2；地块 gisid；建筑 udm-… / bdms-…；楼层 / 单元为 BDMS 编号。
 */
@RestController
@RequestMapping("/api/v2/space")
public class SpaceController {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final SpaceService service;

    public SpaceController(SpaceService service) {
        this.service = service;
    }

    /** 节点视图：名称、上级链、属性、计数、分组、可用图层。 */
    @GetMapping("/nodes/{level}/{key}")
    public ApiResponse<NodeDetail> node(@PathVariable String level, @PathVariable String key) {
        return ApiResponse.ok(service.node(SpaceLevel.of(level), key));
    }

    /** 下一级列表（分页 + 关键字）。 */
    @GetMapping("/nodes/{level}/{key}/children")
    public ApiResponse<PageResult<Map<String, Object>>> children(@PathVariable String level, @PathVariable String key,
                                                                 @RequestParam(required = false) String q,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(service.children(SpaceLevel.of(level), key, q, page, size));
    }

    /** 开发建设页签：总体规划、规划建筑面积、总规实现率、在建工地、地块管控（建筑及以下按所在地块）。 */
    @GetMapping("/nodes/{level}/{key}/dev")
    public ApiResponse<Map<String, Object>> dev(@PathVariable String level, @PathVariable String key) {
        return ApiResponse.ok(service.dev(SpaceLevel.of(level), key));
    }

    /** 市场页签：Tawtheeq 租约与 ADREC 交易（建筑及以下按所在地块），不含个人信息。 */
    @GetMapping("/nodes/{level}/{key}/mkt")
    public ApiResponse<Map<String, Object>> mkt(@PathVariable String level, @PathVariable String key) {
        return ApiResponse.ok(service.mkt(SpaceLevel.of(level), key));
    }

    /** 城市运行页签：路灯与公交站（地块及以下按所在社区）。 */
    @GetMapping("/nodes/{level}/{key}/ops")
    public ApiResponse<Map<String, Object>> ops(@PathVariable String level, @PathVariable String key) {
        return ApiResponse.ok(service.ops(SpaceLevel.of(level), key));
    }

    /** 地图图层（GeoJSON FeatureCollection，EPSG:4326）。 */
    @GetMapping("/nodes/{level}/{key}/layers/{layer}.geojson")
    public ResponseEntity<String> layer(@PathVariable String level, @PathVariable String key, @PathVariable String layer) {
        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .body(service.features(SpaceLevel.of(level), key, SpaceLayer.of(layer)));
    }

    /** 按名称 / 编号搜索区域、社区、地块、建筑。 */
    @GetMapping("/search")
    public ApiResponse<List<Map<String, Object>>> search(@RequestParam String q, @RequestParam(defaultValue = "8") int limit) {
        return ApiResponse.ok(service.search(q, limit));
    }

    /** 主题库各表的来源、行数、最近写入时间，及 ODS 抽取器最近一次运行结果。 */
    @GetMapping("/meta")
    public ApiResponse<Map<String, Object>> meta() {
        return ApiResponse.ok(service.meta());
    }
}
