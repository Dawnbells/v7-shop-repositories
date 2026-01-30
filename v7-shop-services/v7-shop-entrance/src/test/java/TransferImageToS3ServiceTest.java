import cn.hutool.core.io.FileUtil;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.entrance.V7ShopEntranceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SpringBootTest(classes = V7ShopEntranceApplication.class)
public class TransferImageToS3ServiceTest {
    private final IMultimediaFileService multimediaFileService;

    @Autowired
    public TransferImageToS3ServiceTest(IMultimediaFileService multimediaFileService) {
        this.multimediaFileService = multimediaFileService;
    }

    @Test
    public void testTransfer() throws InterruptedException {
//        CountDownLatch latch = new CountDownLatch(1);
//        String path = "E:\\V7Soft\\Workspace\\v7-shop-services\\sources\\v7-shop-services\\transfer.txt";
//        String startStr = FileUtil.readString(path, StandardCharsets.UTF_8);
//        long start = Long.parseLong(startStr);
//        long transfer = start + 1;
//        TenantContext.setCurrentTenant(1L, Company.builder().build());
//        while (transfer > start) {
//            start = transfer;
//            transfer = multimediaFileService.transferImageToS3(start);
//            if (transfer > start) {
//                FileUtil.writeUtf8String(String.valueOf(transfer), path);
//            }
//        }

        System.out.println("has key >> " );
        List<String> strings = FileUtil.readLines(new File("E:\\V7Soft\\Workspace\\v7-shop-services\\sources\\v7-shop-services\\transfer\\error_s3.txt"), StandardCharsets.UTF_8);
        for (String line: strings) {
            System.out.println("has key >> " + line);
            long current = Long.parseLong(line);
//            long next = multimediaFileService.transferImageToS3(current-1);
//            if (next < current) {
//                System.out.println("has no key >> " + current);
//                FileUtil.appendUtf8String(current + "\n", "E:\\V7Soft\\Workspace\\v7-shop-services\\sources\\v7-shop-services\\transfer\\error_s3a.txt");
//            }
        }
        System.out.println("====finish====");
    }
}
