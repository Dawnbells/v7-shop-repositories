package cn.v7soft.dao.entities.primary;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "t_system_settings")
public class SystemSettings {
    @Id
    private int name;
    /**
     * ssl证书申请试用服务
     */
    private String value;
}
