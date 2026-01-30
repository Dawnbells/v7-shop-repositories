package cn.v7soft.dao.entities.primary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 子域名SPU像素关联的复合主键
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubDomainSpuPixelId implements Serializable {
    private Long subDomainId;
    private Long spuId;
    private Long pixelId;
}

