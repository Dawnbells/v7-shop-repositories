import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.LineHandler;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.IdUtil;

public class AddressXLSToSql {

    private static final Map<String, String> idMap = new HashMap<>() {
        {
            put("PL", "1103717400576");
            put("RO", "1103718973440");
            put("GR", "1103720382464");
            put("CZ", "1103725592576");
            put("BG", "1103726231552");
            put("AT", "1103795027968");
            put("HU", "1103808397312");
            put("SI", "1103808708608");
            put("SK", "1103809101824");
            put("HR", "1103809495040");
            put("ES", "1103809970176");
            put("PT", "1103810265088");
            put("IT", "1103810510848");
            put("LV", "1103810789376");
            put("LT", "1103811215360");
            put("EE", "1103811543040");
            put("DE", "1275266367488");
            put("JP", "1275266367489");
            put("EG", "1691497236486");
            put("TH", "1742026347520");
            put("MY", "1930357242887");
        }
    };

    public static final Map<String, String[]> COUNTRY_ADDRESS_MAP = new HashMap<>();

    static {
        COUNTRY_ADDRESS_MAP.put("PL", new String[]{"postal_code", "province", "city"});
        COUNTRY_ADDRESS_MAP.put("RO", new String[]{"postal_code", "province", "city"});
        COUNTRY_ADDRESS_MAP.put("GR", new String[]{"postal_code", "province", "city", "district"});
        COUNTRY_ADDRESS_MAP.put("CZ", new String[]{"postal_code", "province", "city"});
        COUNTRY_ADDRESS_MAP.put("BG", new String[]{"postal_code", "province", "city"});
        COUNTRY_ADDRESS_MAP.put("AT", new String[]{"postal_code", "province", "city"});
        COUNTRY_ADDRESS_MAP.put("HU", new String[]{"postal_code", "province", "city"});
        COUNTRY_ADDRESS_MAP.put("SI", new String[]{"postal_code", "province", "city", "district"});
        COUNTRY_ADDRESS_MAP.put("SK", new String[]{"postal_code", "province", "city"});
        COUNTRY_ADDRESS_MAP.put("HR", new String[]{"id", "province", "city", "district", "postal_code"});
        COUNTRY_ADDRESS_MAP.put("ES", new String[]{"country", "postal_code", "city", "province", "district", "area", "arrive"});
        COUNTRY_ADDRESS_MAP.put("PT", new String[]{"postal_code", "province", "city", "district", "arrive"});
        COUNTRY_ADDRESS_MAP.put("IT", new String[]{"province", "city", "postal_code"});
        COUNTRY_ADDRESS_MAP.put("LV", new String[]{"id", "province", "city", "district", "postal_code"});
        COUNTRY_ADDRESS_MAP.put("LT", new String[]{"postal_code", "province", "city", "district"});
        COUNTRY_ADDRESS_MAP.put("EE", new String[]{"id", "province", "city", "district", "postal_code"});
        COUNTRY_ADDRESS_MAP.put("DE", new String[]{"postal_code", "city", "province"});
        COUNTRY_ADDRESS_MAP.put("JP", new String[]{"postal_code", "province", "city", "district"});
        COUNTRY_ADDRESS_MAP.put("EG", new String[]{"province", "city", "district"});
        COUNTRY_ADDRESS_MAP.put("TH", new String[]{"province", "provinceENG", "city", "cityENG", "postal_code"});
        COUNTRY_ADDRESS_MAP.put("MY", new String[]{"province", "city", "postal_code"});
        COUNTRY_ADDRESS_MAP.put("ID", new String[]{"province", "city"});
    }
    public static void main(String[] args) {
        String countryCode = "ID";
        String filePath = "E:\\V7Soft\\address\\" + countryCode + ".csv";
        String outputPath = "E:\\V7Soft\\address\\" + countryCode + ".sql";

        transferToSql(filePath, outputPath, COUNTRY_ADDRESS_MAP.get(countryCode), countryCode);
    }

//    public static void main(String[] args) {
//        String countryCode = "TH";
//        String filePath = "E:\\V7Soft\\address\\" + countryCode + ".csv";
//        String outFilePath = "E:\\V7Soft\\address\\" + countryCode + ".csv1";
//        File outputFile = new File(outFilePath);
//        FileUtil.del(outputFile);
//        FileUtil.readLines(new File(filePath), StandardCharsets.UTF_8, (LineHandler) line -> {
//            String[] split = line.split("\t");
//            String newLine = split[0] + "/" + split[1] + "\t" + split[2] + "/" + split[3] + "\t" + split[4] + "\n";
//            FileUtil.appendUtf8String(newLine, outputFile);
//        });
//    }

    private static void transferToSql(String filePath, String outputPath, String[] columns, String countryCode) {
        File outputFile = new File(outputPath);
        FileUtil.del(outputFile);
        String tableName = "`t_addresses_" + countryCode.toLowerCase() + "`";
        FileUtil.appendUtf8String("TRUNCATE TABLE " + tableName + ";\n", outputFile);
        FileUtil.readLines(new File(filePath), StandardCharsets.UTF_8, (LineHandler) line -> {
            String[] split = line.split(",");

            if (split.length != columns.length) {
                String[] oldSplit = split;
                split = resolveUnSafeLength(countryCode, split, columns);
                if (split.length != columns.length) {
                    System.err.println("√： columns length error: " + line);
                    return;
                } else {
                    System.out.println("resolve " + ArrayUtil.join(oldSplit, ",") + " to " + ArrayUtil.join(split, ","));
                }
            }

            boolean isArrive = true;
            if (ArrayUtil.contains(columns, "arrive") && !line.contains("YES")) {
                System.out.println("can not arrive: " + line);
                isArrive = false;
            }
            Map<String, String> map = new HashMap<>();
            for (int i = 0; i < columns.length; i++) {
                String column = columns[i];
                String value = split[i];
                map.put(column, value.replace("'", "''"));
            }

            if (!map.getOrDefault("province", "").equals("#N/A")) {
                String sql = "INSERT INTO " + tableName + " (" +
                             "`id`, `create_time`, `status`, `update_time`, " +
                             "`province`, `city`, `district`, `postal_code` " +
                             ") " +
                             "VALUES " +
                             "(" +
                             IdUtil.getSnowflakeNextId() + "," +
                             "'2024-12-03 15:34:01.969505', " + (isArrive ? "'VALID'" : "'INVALID'") +
                             ", '2024-12-03 15:34:01.969505','" +
                             map.getOrDefault("province", "") + "','" +
                             map.getOrDefault("city", "") + "','" +
                             map.getOrDefault("district", "") + "','" +
                             map.getOrDefault("postal_code", "") + "'" +
                             ");\n";
                FileUtil.appendUtf8String(sql, outputFile);
            }
        });
    }

    private static String[] resolveUnSafeLength(String countryCode, String[] split, String[] columns) {
        if ("LT".equalsIgnoreCase(countryCode)) {
            // 地区缺失
            return resolve(split, columns, 3, 2);
        }
        if ("EE".equalsIgnoreCase(countryCode)) {
            // 市缺失
            return resolve(split, columns, 2, 1);
        }
        if ("EG".equalsIgnoreCase(countryCode)) {
            // 区缺失
            return resolve(split, columns, 2, 1);
        }
        return split;
    }

    private static String[] resolve(String[] split, String[] columns, int insertIndex, int copyIndex) {
        String[] result = new String[columns.length];
        int index = 0;
        for(int i = 0; i < columns.length; i++) {
            if (insertIndex == i) {
                result[i] = split[copyIndex];
            } else {
                result[i] = split[index++];
            }
        }
        return result;
    }
}
