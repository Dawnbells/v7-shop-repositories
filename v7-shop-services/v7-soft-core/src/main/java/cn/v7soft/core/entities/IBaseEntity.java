package cn.v7soft.core.entities;

import cn.v7soft.core.enums.StatusEnum;

public interface IBaseEntity {
    Long getId();
    StatusEnum getStatus();
    void setStatus(StatusEnum status);
}
