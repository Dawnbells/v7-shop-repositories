import cn.v7soft.admin.service.IAliyunOssService;
import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.impl.MultimediaFileService;
import cn.v7soft.entrance.V7ShopEntranceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;

@SpringBootTest(classes = V7ShopEntranceApplication.class)
public class AliyunTest {
    private final IAliyunOssService aliyunOssService;
    private final IMultimediaFileService multimediaFileService;

    @Autowired
    public AliyunTest(IAliyunOssService aliyunOssService, IMultimediaFileService multimediaFileService) {
        this.aliyunOssService = aliyunOssService;
        this.multimediaFileService = multimediaFileService;
    }

    @Test
    public void testAliyun() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        latch.await();
    }

}
