import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.LineHandler;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class AnalyzeAccessLog {
    static Map<Long, Long> mapping = new HashMap<>();
    static Map<String, Long> timestampMapping = new HashMap<>();
    static Map<Long, Long> timeLevel = new HashMap<>();
    static int notEndCount;
    static int totalCount;
    static int accessCount;
    static int illegalCount;

    public static void main(String[] args) {
        notEndCount = 0;
        totalCount = 0;
        accessCount = 0;
        illegalCount = 0;
        String filePath = "E:\\V7Soft\\out (4).log";
        FileUtil.readLines(new File(filePath), StandardCharsets.UTF_8, (LineHandler) line -> {
            if (StrUtil.isEmpty(line)) {
                return;
            }
            if (!line.contains("pdVal = ")) {
                return;
            }
            String pdVal = line.substring(32, 64);
            if (line.contains("start")) {
                Long timestamp = Long.parseLong(line.substring(84));
                totalCount++;
                timestampMapping.put(pdVal, timestamp);
            } else if (line.contains("end")) {
                Long timestamp = Long.parseLong(line.substring(82));
                Long start = timestampMapping.get(pdVal);
                if (start != null) {
                    long delta = timestamp - start;
                    Long count = mapping.get(delta);
                    accessCount++;
                    mapping.put(delta, count == null ? 1 : count + 1);
                    Long levelCount = timeLevel.get(delta / 1000);
                    timeLevel.put(delta / 1000, levelCount == null ? 1 : levelCount + 1);
                } else {
                    System.err.println("can't not find start timestamp: " + pdVal);
                    illegalCount++;
                }
            }
        });
        System.out.println("access: " + accessCount + ", effect: " + notEndCount + ", illegal: " + illegalCount + ", total: " + totalCount);
        timeLevel.keySet().stream().sorted().forEach(aLong -> {
            System.out.println(aLong + ": " + timeLevel.get(aLong));
        });
    }
}
