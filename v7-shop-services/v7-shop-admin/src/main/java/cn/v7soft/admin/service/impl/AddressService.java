package cn.v7soft.admin.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import cn.hutool.core.util.IdUtil;
import cn.v7soft.admin.service.IAddressService;
import cn.v7soft.core.enums.ClientResponseEnum;
import cn.v7soft.core.enums.StatusEnum;
import cn.v7soft.core.service.impl.BaseService;
import cn.v7soft.dao.entities.address.Address;
import cn.v7soft.dao.repositories.address.AddressRepository;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AddressService extends BaseService<Address, AddressRepository> implements IAddressService {

    private final JdbcTemplate addressJdbcTemplate;

    private static final Map<String, String> HEADER_MAPPING = new LinkedHashMap<>();

    static {
        HEADER_MAPPING.put("省", "province");
        HEADER_MAPPING.put("省份", "province");
        HEADER_MAPPING.put("市", "city");
        HEADER_MAPPING.put("城市", "city");
        HEADER_MAPPING.put("区", "district");
        HEADER_MAPPING.put("区县", "district");
        HEADER_MAPPING.put("邮编", "postal_code");
        HEADER_MAPPING.put("邮政编码", "postal_code");
        HEADER_MAPPING.put("偏远", "is_remote");
        HEADER_MAPPING.put("是否偏远", "is_remote");
        HEADER_MAPPING.put("偏远提示", "remote_tip");
    }

    private static final Set<String> REMOTE_TRUE_VALUES = Set.of("是", "true", "yes", "1");

    private static final Map<String, String> FIELD_DISPLAY_NAMES = Map.of(
            "province", "省份", "city", "城市", "district", "区县", "postal_code", "邮编"
    );

    public AddressService(AddressRepository repository,
                          @Qualifier("addressDataSource") DataSource addressDataSource) {
        super(repository);
        this.addressJdbcTemplate = new JdbcTemplate(addressDataSource);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public Optional<Address> findById(Long id) {
        return this.repository.findById(id);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public Address save(Address data) {
        checkKeyConstraint(data);
        return this.repository.save(data);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public Address saveAndFlush(Address data) {
        checkKeyConstraint(data);
        return this.repository.saveAndFlush(data);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public void delete(Long id) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        Address t = getById(id);
        t.setStatus(StatusEnum.DELETED);
        save(t);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public void switchStatus(Long id, StatusEnum status) {
        ClientResponseEnum.PARAMETER_ILLEGAL.notNull(id, "ID不能为空");
        Address t = getById(id);
        ClientResponseEnum.PARAMETER_ILLEGAL.assertTrue(t.getStatus() != StatusEnum.DELETED, "已删除");
        t.setStatus(status);
        save(t);
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public void deleteAll(List<Long> ids) {
        @SuppressWarnings("SqlCurrentSchemaInspection")
        String sql = "UPDATE " + getTableName(type) + " SET `status`='DELETED' WHERE id IN :ids";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("ids", ids);
        query.executeUpdate();
    }

    @Override
    protected void checkKeyConstraint(Address data) {
    }

    // ======================== 地址库导入相关 ========================

    @Override
    public List<String> getAddressCountries() {
        List<Map<String, Object>> tables = addressJdbcTemplate.queryForList("SHOW TABLES LIKE 't_addresses_%'");
        return tables.stream()
                .map(row -> {
                    String tableName = row.values().iterator().next().toString();
                    return tableName.replace("t_addresses_", "").toUpperCase();
                })
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> pageByCountry(String countryCode, int pageNo, int pageSize) {
        String tableName = getAddressTableName(countryCode);
        ensureTableExists(tableName);

        int offset = (pageNo - 1) * pageSize;
        String countSql = "SELECT COUNT(*) FROM `" + tableName + "` WHERE status <> 'DELETED'";
        String dataSql = "SELECT id, province, city, district, postal_code, status, create_time, update_time " +
                         "FROM `" + tableName + "` WHERE status <> 'DELETED' ORDER BY province, city, district LIMIT ? OFFSET ?";

        Long total = addressJdbcTemplate.queryForObject(countSql, Long.class);
        List<Map<String, Object>> rows = addressJdbcTemplate.queryForList(dataSql, pageSize, offset);

        Map<String, Object> result = new HashMap<>();
        result.put("list", rows);
        result.put("total", total != null ? total : 0);
        return result;
    }

    @Override
    public Map<String, Object> remoteAreaPage(String countryCode, int pageNo, int pageSize) {
        String cc = countryCode.toUpperCase().trim();
        int offset = (pageNo - 1) * pageSize;

        String countSql = "SELECT COUNT(*) FROM `t_remote_area` WHERE country_code = ? AND status <> 'DELETED'";
        String dataSql = "SELECT id, postal_code, tip, status, create_time, update_time " +
                         "FROM `t_remote_area` WHERE country_code = ? AND status <> 'DELETED' " +
                         "ORDER BY postal_code LIMIT ? OFFSET ?";

        Long total = addressJdbcTemplate.queryForObject(countSql, Long.class, cc);
        List<Map<String, Object>> rows = addressJdbcTemplate.queryForList(dataSql, cc, pageSize, offset);

        Map<String, Object> result = new HashMap<>();
        result.put("list", rows);
        result.put("total", total != null ? total : 0);
        return result;
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public Map<String, Object> importAddresses(String countryCode, MultipartFile file) {
        List<String[]> excelRows = parseExcel(file);
        BiConsumer<Integer, String> noopProgress = (p, m) -> {};
        return doImport(countryCode, excelRows, noopProgress);
    }

    private Map<String, Object> importAddressData(String countryCode, List<String[]> dataRows,
            Map<String, Integer> columnIndex, Set<String> requiredFields,
            boolean hasPostalCode, boolean hasIsRemote, boolean hasRemoteTip,
            BiConsumer<Integer, String> progressCallback) {
        String tableName = getAddressTableName(countryCode);
        ensureTableExists(tableName);

        LocalDateTime now = LocalDateTime.now();
        List<Object[]> addressBatch = new ArrayList<>();
        List<Object[]> remoteBatch = new ArrayList<>();
        Set<String> addressKeys = new LinkedHashSet<>();
        Set<String> remoteKeys = new LinkedHashSet<>();
        int duplicateCount = 0;
        int remoteDuplicateCount = 0;
        int skippedCount = 0;
        List<String> validationErrors = new ArrayList<>();
        int totalRows = dataRows.size();

        for (int i = 0; i < totalRows; i++) {
            String[] row = dataRows.get(i);
            int lineNum = i + 2;
            String province = getColumnValue(row, columnIndex, "province");
            String city = getColumnValue(row, columnIndex, "city");
            String district = getColumnValue(row, columnIndex, "district");
            String postalCode = getColumnValue(row, columnIndex, "postal_code");

            List<String> emptyFields = new ArrayList<>();
            for (String field : requiredFields) {
                if (getColumnValue(row, columnIndex, field).isEmpty()) {
                    emptyFields.add(FIELD_DISPLAY_NAMES.getOrDefault(field, field));
                }
            }
            if (!emptyFields.isEmpty()) {
                skippedCount++;
                if (validationErrors.size() < 5) {
                    validationErrors.add("第" + lineNum + "行: " + String.join("、", emptyFields) + "为空");
                }
                continue;
            }

            String dedupKey = province + "|" + city + "|" + district + "|" + postalCode;
            if (!addressKeys.add(dedupKey)) {
                duplicateCount++;
                continue;
            }

            addressBatch.add(new Object[]{
                    IdUtil.getSnowflakeNextId(), now, "VALID", now,
                    province, city, district, postalCode
            });

            if (hasIsRemote || hasRemoteTip) {
                String isRemote = getColumnValue(row, columnIndex, "is_remote");
                String remoteTip = getColumnValue(row, columnIndex, "remote_tip");
                boolean isRemoteArea = REMOTE_TRUE_VALUES.contains(isRemote.toLowerCase().trim());
                if (isRemoteArea && hasPostalCode && !postalCode.isBlank()) {
                    if (remoteKeys.add(postalCode)) {
                        remoteBatch.add(new Object[]{
                                IdUtil.getSnowflakeNextId(), now, "VALID", now,
                                countryCode.toUpperCase(), postalCode, remoteTip
                        });
                    } else {
                        remoteDuplicateCount++;
                    }
                }
            }

            if (i % 5000 == 0 && i > 0) {
                int percent = 15 + (int) ((i * 50.0) / totalRows);
                progressCallback.accept(Math.min(percent, 65),
                        String.format("校验+去重中: %d/%d 行", i, totalRows));
            }
        }

        if (addressBatch.isEmpty()) {
            throw new IllegalArgumentException("无有效数据行可导入（校验失败 " + skippedCount + " 行，重复 " + duplicateCount + " 行）");
        }

        progressCallback.accept(70, "正在写入地址数据（" + addressBatch.size() + "条）...");
        addressJdbcTemplate.execute("TRUNCATE TABLE `" + tableName + "`");
        String insertSql = "INSERT INTO `" + tableName + "` " +
                           "(`id`, `create_time`, `status`, `update_time`, `province`, `city`, `district`, `postal_code`) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        addressJdbcTemplate.batchUpdate(insertSql, addressBatch);

        if (!remoteBatch.isEmpty()) {
            progressCallback.accept(85, "正在写入偏远数据（" + remoteBatch.size() + "条）...");
            addressJdbcTemplate.update("DELETE FROM `t_remote_area` WHERE country_code = ?", countryCode.toUpperCase());
            String remoteInsertSql = "INSERT INTO `t_remote_area` " +
                                     "(`id`, `create_time`, `status`, `update_time`, `country_code`, `postal_code`, `tip`) " +
                                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
            addressJdbcTemplate.batchUpdate(remoteInsertSql, remoteBatch);
        }

        progressCallback.accept(95, "导入完成，正在生成报告...");
        log.info("导入国家[{}]地址库完成: 地址{}条(去重{}), 偏远{}条(去重{}), 跳过{}",
                countryCode, addressBatch.size(), duplicateCount, remoteBatch.size(), remoteDuplicateCount, skippedCount);

        return buildImportResult("address", addressBatch.size(), duplicateCount,
                remoteBatch.size(), remoteDuplicateCount, skippedCount, validationErrors);
    }

    private Map<String, Object> importRemoteOnly(String countryCode, List<String[]> dataRows,
            Map<String, Integer> columnIndex, boolean hasRemoteTip,
            BiConsumer<Integer, String> progressCallback) {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> remoteBatch = new ArrayList<>();
        Set<String> remoteKeys = new LinkedHashSet<>();
        int duplicateCount = 0;
        int skippedCount = 0;
        List<String> validationErrors = new ArrayList<>();

        for (int i = 0; i < dataRows.size(); i++) {
            String[] row = dataRows.get(i);
            int lineNum = i + 2;
            String postalCode = getColumnValue(row, columnIndex, "postal_code");

            if (postalCode.isEmpty()) {
                skippedCount++;
                if (validationErrors.size() < 5) {
                    validationErrors.add("第" + lineNum + "行: 邮编为空");
                }
                continue;
            }

            if (!remoteKeys.add(postalCode)) {
                duplicateCount++;
                continue;
            }

            String remoteTip = hasRemoteTip ? getColumnValue(row, columnIndex, "remote_tip") : "";
            remoteBatch.add(new Object[]{
                    IdUtil.getSnowflakeNextId(), now, "VALID", now,
                    countryCode.toUpperCase(), postalCode, remoteTip
            });
        }

        if (remoteBatch.isEmpty()) {
            throw new IllegalArgumentException("无有效偏远数据可导入（校验失败 " + skippedCount + " 行，重复 " + duplicateCount + " 行）");
        }

        progressCallback.accept(70, "正在写入偏远数据（" + remoteBatch.size() + "条）...");
        addressJdbcTemplate.update("DELETE FROM `t_remote_area` WHERE country_code = ?", countryCode.toUpperCase());
        String remoteInsertSql = "INSERT INTO `t_remote_area` " +
                                 "(`id`, `create_time`, `status`, `update_time`, `country_code`, `postal_code`, `tip`) " +
                                 "VALUES (?, ?, ?, ?, ?, ?, ?)";
        addressJdbcTemplate.batchUpdate(remoteInsertSql, remoteBatch);

        progressCallback.accept(95, "导入完成，正在生成报告...");
        log.info("导入国家[{}]纯偏远数据完成: {}条(去重{}, 跳过{})", countryCode, remoteBatch.size(), duplicateCount, skippedCount);

        return buildImportResult("remote", 0, 0,
                remoteBatch.size(), duplicateCount, skippedCount, validationErrors);
    }

    private Map<String, Object> buildImportResult(String mode, int addressCount, int addressDuplicateCount,
            int remoteCount, int remoteDuplicateCount, int skippedCount, List<String> validationErrors) {
        StringBuilder msg = new StringBuilder();
        if ("address".equals(mode)) {
            msg.append("导入成功，共").append(addressCount).append("条地址数据");
            if (addressDuplicateCount > 0) msg.append("（去重跳过").append(addressDuplicateCount).append("条）");
            if (remoteCount > 0) {
                msg.append("，偏远地区").append(remoteCount).append("条");
                if (remoteDuplicateCount > 0) msg.append("（去重跳过").append(remoteDuplicateCount).append("条）");
            }
        } else {
            msg.append("偏远数据导入成功，共").append(remoteCount).append("条");
            if (remoteDuplicateCount > 0) msg.append("（去重跳过").append(remoteDuplicateCount).append("条）");
        }
        if (skippedCount > 0) {
            msg.append("，校验跳过").append(skippedCount).append("行");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("msg", msg.toString());
        result.put("mode", mode);
        result.put("addressCount", addressCount);
        result.put("addressDuplicateCount", addressDuplicateCount);
        result.put("remoteCount", remoteCount);
        result.put("remoteDuplicateCount", remoteDuplicateCount);
        result.put("skippedCount", skippedCount);
        if (!validationErrors.isEmpty()) {
            result.put("validationErrors", validationErrors);
        }
        return result;
    }

    private String getAddressTableName(String countryCode) {
        String cc = validateCountryCode(countryCode);
        return "t_addresses_" + cc;
    }

    private String validateCountryCode(String code) {
        String sanitized = code.toLowerCase().trim();
        if (!sanitized.matches("^[a-z]{2,3}$")) {
            throw new IllegalArgumentException("无效的国家代码: " + code);
        }
        return sanitized;
    }

    private void ensureTableExists(String tableName) {
        List<Map<String, Object>> result = addressJdbcTemplate.queryForList(
                "SHOW TABLES LIKE ?", tableName);
        if (result.isEmpty()) {
            String ddl = """
                    CREATE TABLE `%s` (
                      `id` bigint NOT NULL,
                      `create_time` datetime(6) NOT NULL,
                      `status` enum('DELETED','INVALID','VALID') CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                      `update_time` datetime(6) NOT NULL,
                      `city` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                      `district` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                      `postal_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                      `province` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
                      PRIMARY KEY (`id`) USING BTREE,
                      INDEX `idx_status_province_city_district`(`status`, `province`, `city`, `district`) USING BTREE,
                      INDEX `idx_status_province_city`(`status`, `province`, `city`) USING BTREE,
                      INDEX `idx_status_province`(`status`, `province`) USING BTREE
                    ) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = DYNAMIC
                    """.formatted(tableName);
            addressJdbcTemplate.execute(ddl);
            log.info("创建地址表: {}", tableName);
        }
    }

    private List<String[]> parseExcel(MultipartFile file) {
        try {
            return parseExcelFromStream(file.getInputStream());
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取上传文件: " + e.getMessage());
        }
    }

    private List<String[]> parseExcelFromStream(InputStream inputStream) {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("Excel文件中没有工作表");
            }
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                int lastCell = row.getLastCellNum();
                if (lastCell <= 0) continue;
                String[] cells = new String[lastCell];
                boolean hasContent = false;
                for (int c = 0; c < lastCell; c++) {
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String val = cell == null ? "" : formatter.formatCellValue(cell).trim();
                    cells[c] = val;
                    if (!val.isEmpty()) hasContent = true;
                }
                if (hasContent) rows.add(cells);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel文件解析失败: " + e.getMessage());
        }
        return rows;
    }

    @Override
    @Transactional(transactionManager = "addressTransactionManager")
    public Map<String, Object> importAddressesFromFile(String countryCode, String filePath,
                                                       BiConsumer<Integer, String> progressCallback) {
        progressCallback.accept(5, "正在解析Excel文件...");
        List<String[]> excelRows;
        try (InputStream is = new FileInputStream(filePath)) {
            excelRows = parseExcelFromStream(is);
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取文件: " + e.getMessage());
        }
        return doImport(countryCode, excelRows, progressCallback);
    }

    private Map<String, Object> doImport(String countryCode, List<String[]> excelRows,
                                          BiConsumer<Integer, String> progressCallback) {
        if (excelRows.isEmpty()) {
            throw new IllegalArgumentException("Excel文件为空或无有效数据行");
        }

        progressCallback.accept(10, "正在校验表头...");

        String[] headers = excelRows.get(0);
        Map<String, Integer> columnIndex = mapHeaders(headers);

        boolean hasProvince = columnIndex.containsKey("province");
        boolean hasCity = columnIndex.containsKey("city");
        boolean hasDistrict = columnIndex.containsKey("district");
        boolean hasPostalCode = columnIndex.containsKey("postal_code");
        boolean hasIsRemote = columnIndex.containsKey("is_remote");
        boolean hasRemoteTip = columnIndex.containsKey("remote_tip");
        boolean hasAddressColumns = hasProvince || hasCity || hasDistrict;

        if (!hasAddressColumns && !hasPostalCode) {
            throw new IllegalArgumentException("CSV表头缺少必要列（省份/城市/区县/邮编至少需要一个）");
        }
        if (!hasAddressColumns && hasPostalCode && !hasIsRemote && !hasRemoteTip) {
            throw new IllegalArgumentException("仅有邮编列无法判断导入类型，请同时提供\"是否偏远\"或\"偏远提示\"列");
        }

        boolean remoteOnlyMode = !hasAddressColumns && hasPostalCode;
        Set<String> requiredFields = new LinkedHashSet<>();
        if (hasProvince) requiredFields.add("province");
        if (hasCity) requiredFields.add("city");
        if (hasDistrict) requiredFields.add("district");
        if (hasPostalCode) requiredFields.add("postal_code");

        List<String[]> dataRows = excelRows.subList(1, excelRows.size());
        if (dataRows.isEmpty()) {
            throw new IllegalArgumentException("Excel文件无数据行");
        }

        progressCallback.accept(15, "正在校验数据（共" + dataRows.size() + "行）...");

        if (remoteOnlyMode) {
            return importRemoteOnly(countryCode, dataRows, columnIndex, hasRemoteTip, progressCallback);
        }
        return importAddressData(countryCode, dataRows, columnIndex, requiredFields,
                hasPostalCode, hasIsRemote, hasRemoteTip, progressCallback);
    }

    @Override
    public byte[] generateTemplate() {
        String[] headers = {"省份", "城市", "区县", "邮编", "是否偏远", "偏远提示"};
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("地址导入模板");
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            headerStyle.setFont(font);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 15 * 256);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("生成模板失败: " + e.getMessage());
        }
    }

    private Map<String, Integer> mapHeaders(String[] headers) {
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim();
            String mapped = HEADER_MAPPING.get(header);
            if (mapped != null && !columnIndex.containsKey(mapped)) {
                columnIndex.put(mapped, i);
            }
        }
        return columnIndex;
    }

    private String getColumnValue(String[] row, Map<String, Integer> columnIndex, String field) {
        Integer idx = columnIndex.get(field);
        if (idx == null || idx >= row.length) return "";
        return row[idx].trim();
    }
}
