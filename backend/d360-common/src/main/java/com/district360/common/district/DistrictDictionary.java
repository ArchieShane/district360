package com.district360.common.district;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * 统一区字典，配置在 district-dictionary.yml（d360.districts）。
 */
@ConfigurationProperties(prefix = "d360")
public class DistrictDictionary {

    private List<DistrictRef> districts = new ArrayList<>();

    public List<DistrictRef> getDistricts() {
        return districts;
    }

    public void setDistricts(List<DistrictRef> districts) {
        this.districts = districts;
    }

    public List<DistrictRef> all() {
        return List.copyOf(districts);
    }

    public Optional<DistrictRef> find(String code) {
        return districts.stream().filter(d -> d.code().equals(code)).findFirst();
    }

    public DistrictRef require(String code) {
        return find(code).orElseThrow(() -> new NoSuchElementException("未知的区编码: " + code));
    }
}
