package cn.v7soft.core.utils;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.slf4j.LoggerFactory;

import cn.hutool.core.lang.Singleton;
import cn.hutool.json.JSONUtil;
import cn.v7soft.core.entities.BaseEntity;

/**
 * 雪花算法ID生成器
 */
public class V7IdentifierGenerator implements IdentifierGenerator {

    private static final String V7_IDENTIFIER_WORKER_ID = "V7_IDENTIFIER_WORKER_ID";
    private static final String V7_IDENTIFIER_DATACENTER_ID = "V7_IDENTIFIER_DATACENTER_ID";
    private static final List<String> ignoreClassesNames = new ArrayList<>();

    public static void addIgnoreClass(Class<? extends BaseEntity> clazz) {
        ignoreClassesNames.add(clazz.getName());
        LoggerFactory.getLogger(V7IdentifierGenerator.class).debug("v7 identifier add ignore class: {}", clazz.getName());
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public Long generate(SharedSessionContractImplementor session, Object object) {
        for (String ignoreClassName : ignoreClassesNames) {
            boolean ignore = ClassUtils.isInstanceOf(object, ignoreClassName);
            if (ignore) {
                return ClassUtils.invokeGetId(object);
            }
        }
        String workerIdStr = System.getenv(V7_IDENTIFIER_WORKER_ID);
        String datacenterStr = System.getenv(V7_IDENTIFIER_DATACENTER_ID);
        long workerId = parseLong(workerIdStr);
        long datacenterId = parseLong(datacenterStr);
        return Singleton.get(ShortSnowflake.class, workerId, datacenterId).nextId();
    }

    public static Long generateId() {
        String workerIdStr = System.getenv(V7_IDENTIFIER_WORKER_ID);
        String datacenterStr = System.getenv(V7_IDENTIFIER_DATACENTER_ID);
        long workerId = parseLong(workerIdStr);
        long datacenterId = parseLong(datacenterStr);
        return Singleton.get(ShortSnowflake.class, workerId, datacenterId).nextId();
    }
}
