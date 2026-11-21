package com.district360.app.service;

import com.district360.app.config.LandUseProperties;
import com.district360.app.etl.EtlService;
import com.district360.app.web.dto.BlockStatus;
import com.district360.app.web.dto.DistrictListItem;
import com.district360.app.web.dto.DistrictOverview;
import com.district360.app.web.dto.MetaInfo;
import com.district360.common.district.DistrictDictionary;
import com.district360.stat.mapper.StatQueryMapper;
import com.district360.stat.row.District360Row;
import com.district360.stat.row.DistrictListRow;
import com.district360.stat.row.LandUseRow;
import com.district360.stat.row.SourceStatusRow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** 只读统计库，组装接口返回。 */
@Service
public class DistrictService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String MUNICIPALITY = "ADM";

    private final StatQueryMapper query;
    private final DistrictDictionary dictionary;
    private final LandUseProperties landUse;
    private final EtlService etl;

    public DistrictService(StatQueryMapper query, DistrictDictionary dictionary, LandUseProperties landUse, EtlService etl) {
        this.query = query;
        this.dictionary = dictionary;
        this.landUse = landUse;
        this.etl = etl;
    }

    public List<DistrictListItem> listDistricts() {
        boolean livLoaded = rowCount("liveability") > 0;
        return query.listDistricts().stream().map(r -> new DistrictListItem(
                r.code, r.nameEn, r.nameAr, r.sortNo, r.mappingConfirmed, r.mappingNote,
                r.makaniDistrictId, r.livDistrictId,
                r.centroidLon == null ? null : new double[]{r.centroidLon, r.centroidLat},
                r.minLon == null ? null : new double[]{r.minLon, r.minLat, r.maxLon, r.maxLat},
                r.areaKm2,
                r.scoreCurrent,
                livStatus(r.livPresent, Boolean.TRUE.equals(r.livSample), livLoaded))).toList();
    }

    public DistrictOverview overview(String code) {
        dictionary.require(code);
        District360Row r = query.findDistrict360(code);
        if (r == null) {
            throw new NoSuchElementException("统计库里没有这个区: " + code);
        }
        boolean makani = r.plots != null;
        boolean livPresent = r.scoreCurrent != null || r.livAsOf != null;
        boolean adrecPresent = r.adrecAsOf != null || r.transactions != null;
        boolean livLoaded = rowCount("liveability") > 0;

        String makaniStatus = makani ? BlockStatus.OK : BlockStatus.UNAVAILABLE;
        String livStatus = livStatus(livPresent, Boolean.TRUE.equals(r.livSample), livLoaded);
        String adrecStatus = !adrecPresent ? BlockStatus.UNAVAILABLE
                : Boolean.TRUE.equals(r.adrecSample) ? BlockStatus.SAMPLE : BlockStatus.OK;

        var header = new DistrictOverview.Header(r.code, r.nameEn, r.nameAr, MUNICIPALITY, r.areaKm2, r.communities, r.plots,
                r.mappingConfirmed, r.mappingNote);

        BigDecimal gap = (r.scoreTarget != null && r.scoreCurrent != null)
                ? r.scoreTarget.subtract(r.scoreCurrent).max(BigDecimal.ZERO) : null;
        var liveability = new DistrictOverview.Liveability(livStatus, r.livAsOf, r.scoreExisting, r.scoreCurrent, r.scoreTarget, gap);

        var land = new DistrictOverview.Land(makaniStatus, r.makaniAsOf, r.plots, r.buildings, r.buildingsUnderConstruction,
                pct(r.plotsConstructed, r.plots),
                new DistrictOverview.PlotStatus(r.plotsConstructed, r.plotsUnderConstruction, r.plotsVacantServiced, r.plotsVacantUnserviced),
                makani ? landUseShares(query.listLandUse(code)) : List.of());

        var realEstate = new DistrictOverview.RealEstate(adrecStatus, r.adrecAsOf,
                r.periodStartMonth == null ? null : r.periodStartMonth.format(YM),
                r.periodEndMonth == null ? null : r.periodEndMonth.format(YM),
                r.salesAed == null ? null : r.salesAed.divide(BigDecimal.valueOf(1_000_000_000L), 2, RoundingMode.HALF_UP),
                r.salesYoyPct, r.transactions, r.mortgages, r.avgPriceSqm, r.activeLeases,
                adrecPresent ? query.listAdrecMonthly(code).stream()
                        .map(m -> new DistrictOverview.MonthlyCount(m.month.format(YM), m.transactions)).toList() : List.of());

        var parks = new DistrictOverview.Parks(livStatus, r.livAsOf, r.parks, r.parkAreaHa, r.parkGapHa);
        var facilities = new DistrictOverview.Facilities(makaniStatus, r.makaniAsOf, r.schools, r.medical, r.mosques, parks);

        return new DistrictOverview(header, liveability, land, realEstate, facilities);
    }

    public String boundaries() {
        return query.districtBoundariesGeoJson();
    }

    public byte[] buildingTile(int z, int x, int y) {
        var tile = query.buildingTile(z, x, y);
        return tile == null || tile.mvt == null ? new byte[0] : tile.mvt;
    }

    public MetaInfo meta() {
        List<MetaInfo.SourceInfo> sources = new ArrayList<>();
        for (SourceStatusRow s : query.listSourceStatus()) {
            sources.add(new MetaInfo.SourceInfo(s.source, etl.has(s.source), s.rowCount, s.sourceAsOf,
                    Boolean.TRUE.equals(s.sample), s.lastStatus, s.lastFinishedAt, s.lastMessage));
        }
        return new MetaInfo(dictionary.all().size(), 4, sources);
    }

    // ------------------------------------------------------------------------------------------

    private int rowCount(String source) {
        return query.listSourceStatus().stream().filter(s -> source.equals(s.source)).mapToInt(s -> s.rowCount).sum();
    }

    private static String livStatus(boolean present, boolean sample, boolean sourceLoaded) {
        if (present) {
            return sample ? BlockStatus.SAMPLE : BlockStatus.OK;
        }
        return sourceLoaded ? BlockStatus.NOT_ASSESSED : BlockStatus.UNAVAILABLE;
    }

    private List<DistrictOverview.LandUseShare> landUseShares(List<LandUseRow> rows) {
        List<LandUseProperties.Group> groups = landUse.getGroups();
        Map<String, String> groupOfUse = new HashMap<>();
        groups.forEach(g -> g.uses().forEach(u -> groupOfUse.put(u.toLowerCase(), g.key())));
        String fallback = groups.isEmpty() ? "other" : groups.get(groups.size() - 1).key();

        Map<String, Integer> counts = new LinkedHashMap<>();
        groups.forEach(g -> counts.put(g.key(), 0));
        int total = 0;
        for (LandUseRow r : rows) {
            String key = groupOfUse.getOrDefault(r.useDesc == null ? "" : r.useDesc.toLowerCase(), fallback);
            counts.merge(key, r.plotCount, Integer::sum);
            total += r.plotCount;
        }
        List<DistrictOverview.LandUseShare> out = new ArrayList<>();
        for (LandUseProperties.Group g : groups) {
            int n = counts.getOrDefault(g.key(), 0);
            out.add(new DistrictOverview.LandUseShare(g.key(), g.nameZh(), g.nameEn(), n, pct(n, total)));
        }
        return out;
    }

    private static BigDecimal pct(Integer part, Integer total) {
        if (part == null || total == null || total == 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
    }
}
