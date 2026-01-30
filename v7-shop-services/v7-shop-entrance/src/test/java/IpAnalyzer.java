import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

import cn.hutool.core.io.FileUtil;

public class IpAnalyzer {

    public static void main(String[] args) {
        String filePath = "E:\\V7Soft\\analyzer\\dwd.txt";
        String filePathHt = "E:\\V7Soft\\analyzer\\ht.txt";
        List<String[]> listDwd = FileUtil.readLines(new File(filePath), StandardCharsets.UTF_8)
                .stream()
                .map(line -> line.split("\t"))
                .sorted((Comparator.comparing(split -> Integer.parseInt(split[1]))))
                .toList();
        List<String[]> listHt = FileUtil.readLines(new File(filePathHt), StandardCharsets.UTF_8)
                .stream()
                .map(line -> line.split("\t"))
                .sorted((Comparator.comparing(split -> Integer.parseInt(split[1]))))
                .toList();

        List<String> htIps = listHt.stream().map(hs -> hs[0]).toList();
        List<String> dwdIps = listDwd.stream().map(hs -> hs[0]).toList();

        List<String> intersection = listDwd.stream()
                .filter(split -> htIps.contains(split[0]))
//                .peek(split -> System.out.println("DWD and HT both have ip: " + split[0] + ", count: " + split[1]))
                .map(split -> split[0])
                .toList();

        listDwd.stream()
                .filter(split -> !intersection.contains(split[0]) && Integer.parseInt(split[1]) > 5)
                .forEach(split -> System.out.println("DWD only ip: " + split[0] + ", count: " + split[1]));

        listHt.stream()
                .filter(split -> !intersection.contains(split[0]) && Integer.parseInt(split[1]) > 5)
                .forEach(split -> System.out.println("HT only ip: " + split[0] + ", count: " + split[1]));

    }
}
