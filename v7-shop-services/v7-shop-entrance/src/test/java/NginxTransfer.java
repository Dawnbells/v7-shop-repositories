import cn.hutool.core.io.FileUtil;
import cn.v7soft.admin.utils.NginxConfigWriter;

import java.io.File;
import java.util.Objects;

public class NginxTransfer {
    public static void main(String[] args) {
        File sourceDir = FileUtil.file("E:\\V7Soft\\Repositories\\v7-shop\\sources\\dwd-sync\\nginx\\prod-dwd-eu-fra");
        File targetDir = FileUtil.file("E:\\V7Soft\\Repositories\\v7-shop\\sources\\dwd-sync\\nginx\\prod-dwd-fsn-01");
        for (File file : Objects.requireNonNull(sourceDir.listFiles())) {
            String fileName = file.getName();
            System.out.printf("fileName = " + fileName);
            if (fileName.endsWith(".conf")) {
                File targetFile = new File(targetDir, fileName);
                String domain = fileName.replace(".conf", "");
            }
        }
    }
}
