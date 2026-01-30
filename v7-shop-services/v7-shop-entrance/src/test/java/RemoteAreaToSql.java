import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.LineHandler;
import cn.hutool.core.util.IdUtil;

public class RemoteAreaToSql {

    public static void main(String[] args) {
        String countryCode = "IT";
        String filePath = "E:\\V7Soft\\address\\REMOTE_" + countryCode + ".csv";
        String outputPath = "E:\\V7Soft\\address\\REMOTE_" + countryCode + ".sql";

        transferToSql(filePath, outputPath, countryCode);
    }

//    public static void main(String[] args) {
//        String countryCode = "IT";
//        String fullCode = "E:\\V7Soft\\address\\it_code.csv";
//        String filePath = "E:\\V7Soft\\address\\it_can_code.csv";
//        String outputPath = "E:\\V7Soft\\address\\remoteit.csv";
//        List<String> fullCodes = FileUtil.readLines(fullCode, StandardCharsets.UTF_8);
//        List<String> canCodes = FileUtil.readLines(filePath, StandardCharsets.UTF_8);
//        FileUtil.del(outputPath);
//        fullCodes.stream().filter(code -> !canCodes.contains(code)).collect(Collectors.toSet()).forEach(code -> {
//            FileUtil.appendUtf8String(code + "\n", new File(outputPath));
//        });
//    }

    private static void transferToSql(String filePath, String outputPath, String countryCode) {
        Map<String, Boolean> hasAdded = new HashMap<>();
        File outputFile = new File(outputPath);
        FileUtil.del(outputFile);
        String tableName = "`t_remote_area`";
        FileUtil.appendUtf8String("DELETE FROM " + tableName + " WHERE country_code = '" + countryCode + "';\n", outputFile);
        FileUtil.readLines(new File(filePath), StandardCharsets.UTF_8, (LineHandler) line -> {
            String[] split = line.split("\t");
            String postalCode = split[0].trim();
            if (hasAdded.containsKey(postalCode)) {
                return;
            }
            hasAdded.put(postalCode, true);
            String tip = split.length > 1 ? split[1].trim() : "";
            String sql = "INSERT INTO " + tableName + " (" +
                         "`id`, `create_time`, `status`, `update_time`, " +
                         "`country_code`, `postal_code`, `tip`" +
                         ") " +
                         "VALUES " +
                         "(" +
                         IdUtil.getSnowflakeNextId() + "," +
                         "'2024-12-03 15:34:01.969505', " + "'VALID'" +
                         ", '2024-12-03 15:34:01.969505', '" +
                         countryCode + "','" +
                         postalCode + "', '" +
                         tip + "'" +
                         ");\n";
            FileUtil.appendUtf8String(sql, outputFile);
        });
    }
}
