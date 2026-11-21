package com.district360.app.web;

import com.district360.app.service.DistrictService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/map")
public class MapController {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");
    private static final MediaType MVT = MediaType.parseMediaType("application/x-protobuf");

    private final DistrictService service;

    public MapController(DistrictService service) {
        this.service = service;
    }

    /** 17 区边界（简化后），GeoJSON FeatureCollection，EPSG:4326。 */
    @GetMapping("/districts.geojson")
    public ResponseEntity<String> districts() {
        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES))
                .body(service.boundaries());
    }

    /** 建筑体块矢量瓦片（图层名 buildings，属性 code / height / uc）。 */
    @GetMapping("/tiles/buildings/{z}/{x}/{y}.pbf")
    public ResponseEntity<byte[]> buildings(@PathVariable int z, @PathVariable int x, @PathVariable int y) {
        return tile(z, x, y, () -> service.buildingTile(z, x, y));
    }

    /** 地块矢量瓦片（图层名 plots，属性 code / plot_id / status / use_desc），选中区后按建设状态高亮。 */
    @GetMapping("/tiles/plots/{z}/{x}/{y}.pbf")
    public ResponseEntity<byte[]> plots(@PathVariable int z, @PathVariable int x, @PathVariable int y) {
        return tile(z, x, y, () -> service.plotTile(z, x, y));
    }

    private ResponseEntity<byte[]> tile(int z, int x, int y, java.util.function.Supplier<byte[]> body) {
        int max = 1 << Math.max(0, Math.min(z, 22));
        if (z < 0 || z > 22 || x < 0 || y < 0 || x >= max || y >= max) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok()
                .contentType(MVT)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS))
                .body(body.get());
    }
}
