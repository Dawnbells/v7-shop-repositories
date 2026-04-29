package cn.v7soft.admin.configurer;

import cn.dev33.satoken.secure.BCrypt;
import cn.v7soft.dao.entities.primary.SystemRouter;
import cn.v7soft.dao.entities.primary.SystemUser;
import cn.v7soft.dao.enums.Gender;
import cn.v7soft.dao.enums.SystemUserType;
import cn.v7soft.dao.repositories.primary.SystemUserRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

/**
 * 初始化域名证书申请
 */
@Slf4j
@Component
@AllArgsConstructor
public class DatabaseInitiator implements ApplicationRunner {
    private final SystemUserRepository systemUserRepository;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        ensureAiQuotaColumns();
        ensureFullTextIndexes();
        Optional<SystemUser> admin = systemUserRepository.findById(1L);
        if (admin.isPresent()) {
            return;
        }
        initAdminUser();
        initSystemRouter();
    }

    private void ensureAiQuotaColumns() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            String database = conn.getCatalog();
            addColumnIfMissing(stmt, database, "t_ai_accounts", "daily_limit", "INT NULL");
            addColumnIfMissing(stmt, database, "t_ai_token_usage_records", "ai_account_id", "BIGINT NULL");
            if (!indexExists(stmt, database, "t_ai_token_usage_records", "idx_atur_ai_account_create_time")) {
                stmt.execute("ALTER TABLE t_ai_token_usage_records ADD INDEX idx_atur_ai_account_create_time(ai_account_id, create_time)");
                log.info("Created INDEX idx_atur_ai_account_create_time on t_ai_token_usage_records");
            }
        } catch (Exception e) {
            log.warn("Failed to ensure AI quota columns: {}", e.getMessage());
        }
    }

    private void addColumnIfMissing(Statement stmt, String database, String table, String column, String definition) {
        if (columnExists(stmt, database, table, column)) {
            return;
        }
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            log.info("Created COLUMN {}.{}", table, column);
        } catch (Exception e) {
            log.warn("COLUMN creation skipped for {}.{}: {}", table, column, e.getMessage());
        }
    }

    private boolean columnExists(Statement stmt, String database, String table, String column) {
        String sql = "SELECT 1 FROM information_schema.columns WHERE table_schema = '"
                + database + "' AND table_name = '" + table + "' AND column_name = '" + column + "' LIMIT 1";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureFullTextIndexes() {
        String[][] indexes = {
                {"t_orders", "ft_idx_address", "address"},
                {"t_order_items", "ft_idx_title", "title"},
                {"t_order_items", "ft_idx_merchandise", "merchandise"},
        };
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            for (String[] idx : indexes) {
                String table = idx[0], indexName = idx[1], column = idx[2];
                if (indexExists(stmt, conn.getCatalog(), table, indexName)) {
                    continue;
                }
                try {
                    String ddl = "ALTER TABLE " + table + " ADD FULLTEXT INDEX " + indexName + "(" + column + ") WITH PARSER ngram";
                    stmt.execute(ddl);
                    log.info("Created FULLTEXT INDEX {} on {}.{}", indexName, table, column);
                } catch (Exception e) {
                    log.warn("FULLTEXT INDEX creation skipped for {}: {}", indexName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to ensure FULLTEXT indexes: {}", e.getMessage());
        }
    }

    private boolean indexExists(Statement stmt, String database, String table, String indexName) {
        String sql = "SELECT 1 FROM information_schema.statistics WHERE table_schema = '"
                + database + "' AND table_name = '" + table + "' AND index_name = '" + indexName + "' LIMIT 1";
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private void initAdminUser() {
        SystemUser systemUser = SystemUser.builder()
                .id(1L)
                .companyId(1L)
                .name("管理员")
                .gender(Gender.MALE)
                .telephone("15880411714")
                .userType(SystemUserType.ADMIN)
                .plainPassword("")
                .password(BCrypt.hashpw("Wq2024"))
                .build();
        systemUser.setId(1L);
        systemUserRepository.saveAndFlush(systemUser);
    }

    private void initSystemRouter() {
        SystemRouter.builder().build();
    }
}
