package cn.v7soft.admin.controller.resp;

import cn.v7soft.core.controller.response.IdResponse;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.entities.primary.ThemeTemplate;
import cn.v7soft.dao.enums.ShareType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Getter
@Setter
@SuperBuilder
@Schema(description = "主题模板响应")
public class ThemeTemplateResponse extends IdResponse {
    
    @Schema(title = "模板名称")
    private String name;
    
    @Schema(title = "模板描述")
    private String description;
    
    @Schema(title = "封面图URL")
    private String coverImage;
    
    @Schema(title = "主题配置JSON")
    private String themeConfig;
    
    @Schema(title = "变量结构JSON")
    private String variableSchema;
    
    @Schema(title = "站点配置JSON")
    private String siteConfig;
    
    @Schema(title = "变量值JSON")
    private String variableValues;
    
    @Schema(title = "共享类型")
    private ShareType shareType;
    
    @Schema(title = "共享类型名称")
    private String shareTypeName;
    
    @Schema(title = "复制来源模板ID")
    private Long sharedFromId;
    
    @Schema(title = "复制来源模板名称")
    private String sharedFromName;
    
    @Schema(title = "所有者名称")
    private String ownerName;
    
    @Schema(title = "所有者部门")
    private String ownerDepartment;
    
    @Schema(title = "创建时间")
    private LocalDateTime createTime;
    
    @Schema(title = "更新时间")
    private LocalDateTime updateTime;

    public static ThemeTemplateResponse convertEntity(ThemeTemplate entity) {
        if (entity == null) {
            return null;
        }
        
        SystemUser owner = entity.getOwner();
        String ownerName = "";
        String ownerDepartment = "";
        if (owner != null) {
            ownerName = owner.getName();
            if (owner.getDepartment() != null) {
                ownerDepartment = owner.getDepartment().getName();
            }
        }
        
        ThemeTemplate sharedFrom = entity.getSharedFrom();
        Long sharedFromId = null;
        String sharedFromName = null;
        if (sharedFrom != null) {
            sharedFromId = sharedFrom.getId();
            sharedFromName = sharedFrom.getName();
        }
        
        return ThemeTemplateResponse.builder()
                .id(String.valueOf(entity.getId()))
                .name(entity.getName())
                .description(entity.getDescription())
                .coverImage(entity.getCoverImage())
                .themeConfig(entity.getThemeConfig())
                .variableSchema(entity.getVariableSchema())
                .siteConfig(entity.getSiteConfig())
                .variableValues(entity.getVariableValues())
                .shareType(entity.getShareType())
                .shareTypeName(entity.getShareType() != null ? entity.getShareType().getName() : null)
                .sharedFromId(sharedFromId)
                .sharedFromName(sharedFromName)
                .ownerName(ownerName)
                .ownerDepartment(ownerDepartment)
                .createTime(entity.getCreateTime())
                .updateTime(entity.getUpdateTime())
                .build();
    }
}
