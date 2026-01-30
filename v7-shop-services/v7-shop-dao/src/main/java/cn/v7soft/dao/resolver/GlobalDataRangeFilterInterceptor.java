package cn.v7soft.dao.resolver;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GlobalDataRangeFilterInterceptor implements StatementInspector {

    @Override
    public String inspect(String sql) {
//        log.debug("sql = " + sql);
        return sql;
    }
}
