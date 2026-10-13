package cn.v7soft.admin.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** All Java push entry points must wait for the process while holding the workspace lock. */
public final class NginxPublisher {
    private NginxPublisher() {}

    public static String push() {
        return push(Path.of("/www/nginx"), () ->
                new ProcessBuilder("sh", "/scripts/push.sh").redirectErrorStream(true).start());
    }

    static String push(Path nginxRoot, ProcessStarter starter) {
        try (var ignored = NginxWorkspace.lock()) {
            NginxCertificateReferences.validate(nginxRoot);
            Process process = starter.start();
            try {
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int code = process.waitFor();
                if (code != 0) throw new IllegalStateException("push.sh 执行失败，退出码: " + code);
                return output;
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    // Never release the lock while the publisher can still read the workspace.
                    process.onExit().join();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("校验证书引用或启动 push.sh 失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待 push.sh 完成时被中断", e);
        }
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start() throws IOException;
    }
}
