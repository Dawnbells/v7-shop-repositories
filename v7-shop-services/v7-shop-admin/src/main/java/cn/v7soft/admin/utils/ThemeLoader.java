package cn.v7soft.admin.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.list.UnmodifiableList;
import org.apache.commons.compress.utils.Lists;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import cn.hutool.core.io.IoUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.v7soft.admin.dao.ThemeConfig;
import lombok.Getter;

public class ThemeLoader {

    @Getter
    private static UnmodifiableList<ThemeConfig> themes = new UnmodifiableList<>(Lists.newArrayList());

    public static void loadAllThemes() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // 读取所有 json
        Resource[] resources = resolver.getResources("classpath:themes/*.json");
        List<ThemeConfig> themes = new ArrayList<>();
        for (Resource res : resources) {
            try (InputStream is = res.getInputStream()) {
                JSONObject node = JSONUtil.parseObj(IoUtil.readUtf8(is));
                themes.add(new ThemeConfig(node));
            }
        }
        ThemeLoader.themes = new UnmodifiableList<>(themes);
    }

}
