package cn.v7soft.admin.controller.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RemarkRiskDetailResponse {
    /**
     * 标签
     */
    @Schema(title = "标签", example = "true")
    private List<String> labels;
    /**
     * 风险等级
     */
    @Schema(title = "风险等级", example = "true")
    private String riskLevel;
    /**
     * 风险词
     */
    @Schema(title = "风险词", example = "true")
    private List<String> riskWords;
}
