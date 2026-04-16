package cn.v7soft.core.configurer;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.type.StandardBasicTypes;

/**
 * MySQL
 *
 * @author jiangjt
 */
@SuppressWarnings("unused")
public class MySQLDialectConfig extends org.hibernate.dialect.MySQLDialect {

    @Override
    public String getTableTypeString() {
        return "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
    }

    @Override
    public void initializeFunctionRegistry(FunctionContributions functionContributions) {
        super.initializeFunctionRegistry(functionContributions);
        functionContributions.getFunctionRegistry().registerPattern(
                "match_against",
                "MATCH(?1) AGAINST(?2 IN BOOLEAN MODE)",
                functionContributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.DOUBLE)
        );
    }
}
