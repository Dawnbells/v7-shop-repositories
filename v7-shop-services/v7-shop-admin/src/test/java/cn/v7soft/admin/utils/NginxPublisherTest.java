package cn.v7soft.admin.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NginxPublisherTest {
    @TempDir Path root;

    @Test
    void refusesToLaunchScriptWithMissingCertificate() throws Exception {
        Files.writeString(root.resolve("shop.conf"), "ssl_certificate \""
                + root.resolve("missing.pem").toString().replace('\\', '/') + "\";");
        NginxPublisher.ProcessStarter starter = mock(NginxPublisher.ProcessStarter.class);
        assertThrows(IllegalStateException.class, () -> NginxPublisher.push(root, starter));
        verifyNoInteractions(starter);
    }

    @Test
    void waitsForSuccessfulProcessAndReturnsOutput() throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream("published".getBytes()));
        when(process.waitFor()).thenReturn(0);
        assertEquals("published", NginxPublisher.push(root, () -> process));
        verify(process).waitFor();
    }

    @Test
    void nonzeroProcessExitIsFailure() throws Exception {
        Process process = mock(Process.class);
        when(process.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(process.waitFor()).thenReturn(1);
        assertThrows(IllegalStateException.class, () -> NginxPublisher.push(root, () -> process));
    }
}
