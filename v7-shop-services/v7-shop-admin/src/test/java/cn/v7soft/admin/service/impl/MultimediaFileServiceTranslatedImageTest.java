package cn.v7soft.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import javax.imageio.ImageIO;

import cn.v7soft.admin.service.IMultimediaFileService;
import cn.v7soft.admin.service.IS3Service;
import cn.v7soft.dao.entities.primary.MultimediaFile;
import cn.v7soft.dao.properties.MultimediaFileProperty;
import cn.v7soft.dao.repositories.primary.MultimediaFileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;

class MultimediaFileServiceTranslatedImageTest {

    private final IS3Service s3Service = mock(IS3Service.class);
    private final IMultimediaFileService self = mock(IMultimediaFileService.class);

    private MultimediaFileService service() {
        MultimediaFileService service = new MultimediaFileService(
                mock(MultimediaFileRepository.class), mock(MultimediaFileProperty.class),
                mock(FolderService.class), s3Service);
        service.setMultimediaFileService(self);
        return service;
    }

    @Test
    void failedUploadNeverCreatesAFileRecord() {
        doThrow(ApiCallTimeoutException.create(90_000))
                .when(s3Service).upload(any(byte[].class), anyString(), anyString(), any(Duration.class));

        assertThrows(ApiCallTimeoutException.class, () -> service().saveTranslatedImage(png(3, 2), "png", null));
        verify(self, never()).saveAndFlush(any());
    }

    @Test
    void uploadIsBoundedAndDimensionsComeFromTheImageHeader() throws Exception {
        when(self.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        byte[] png = png(640, 480);

        MultimediaFile saved = service().saveTranslatedImage(png, "png", null);

        ArgumentCaptor<Duration> timeout = ArgumentCaptor.forClass(Duration.class);
        verify(s3Service).upload(eq(png), eq(saved.getRelativePath()), eq("image/png"), timeout.capture());
        // 必须短于插件 120s 的回传超时，服务端先失败才能回 503 让插件重投
        assertTrue(timeout.getValue().compareTo(Duration.ofSeconds(120)) < 0);
        assertEquals(640, saved.getWidth());
        assertEquals(480, saved.getHeight());
    }

    @Test
    void unreadableImageKeepsZeroDimensions() throws Exception {
        assertArrayEquals(new int[] {0, 0}, MultimediaFileService.readImageSize(new byte[] {1, 2, 3}));
    }

    private static byte[] png(int width, int height) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
