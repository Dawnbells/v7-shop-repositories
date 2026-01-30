package cn.v7soft.dao.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public class IdDto {
    private String id;
    public Long getLongId() {
        return Long.valueOf(id);
    }
}
