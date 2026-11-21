package com.district360.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 土地利用归类（d360.land-use）。没被任何组列出的 makani 用途归入最后一组；highlight 的类别显示在面板上。 */
@ConfigurationProperties(prefix = "d360.land-use")
public class LandUseProperties {

    private List<Group> groups = new ArrayList<>();

    public List<Group> getGroups() { return groups; }
    public void setGroups(List<Group> groups) { this.groups = groups; }

    public record Group(String key, String nameZh, String nameEn, boolean highlight, List<String> uses) {
    }
}
