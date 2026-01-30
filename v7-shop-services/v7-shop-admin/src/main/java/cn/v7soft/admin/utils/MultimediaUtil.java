package cn.v7soft.admin.utils;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.common.utils.ConvertUtils;
import cn.v7soft.dao.enums.MediaType;
import cn.v7soft.dao.tenant.TenantContext;

public class MultimediaUtil {

    public static String resolveAbsolutionPath(Long id) {
        String imageBaseUrl = TenantContext.getImageBaseUrl();
        return imageBaseUrl + "/" + id;
    }

    /**
     * 资源相对路径：
     * 公司ID/资源类型/yyyy/MM/dd/文件名UUID.后缀
     */
    public static String makeRelativePath(MediaType mediaType, String tempFileName, String suffix, LocalDateTime now) {
        String year = LocalDateTimeUtil.format(now, "yyyy");
        String month = LocalDateTimeUtil.format(now, "MM");
        String day = LocalDateTimeUtil.format(now, "dd");
        return mediaType.name() + "/" + year + "/" + month + "/" + day + "/" + tempFileName + "." + suffix;
    }

    public static String removeAllPrefix(String introduction) {
        if (StrUtil.isBlank(introduction)) {
            return "";
        }
        String imageBaseUrl = TenantContext.getImageBaseUrl();
        introduction = introduction.replaceAll(imageBaseUrl, "/multimedia");
        // 正则匹配 src="/multimedia/xxx?..."
        String pattern = "(<img[^>]*?src=\")(/multimedia/\\d+)(\\?[^\"']*)?(\"[^>]*?>)";
        // 替换为只保留 /multimedia/{id}
        return introduction.replaceAll(pattern, "$1$2$4");
    }

    public static String replacementIntroductionsMultimedia(IMultimediaFileService multimediaFileService,
                                                            String introduction) {
        // 创建正则表达式
        String regex = "/multimedia/([0-9]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(introduction);

        // 使用 StringBuffer 存储替换结果
        StringBuilder result = new StringBuilder();

        // 替换所有匹配项
        while (matcher.find()) {
            String id = matcher.group(1);
            matcher.appendReplacement(result, resolveAbsolutionPath(ConvertUtils.parseLong(id)));
        }
        matcher.appendTail(result); // 添加剩余部分
        return result.toString();
    }
}
