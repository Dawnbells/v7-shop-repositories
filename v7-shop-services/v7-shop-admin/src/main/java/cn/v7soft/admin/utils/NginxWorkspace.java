package cn.v7soft.admin.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes cooperating JVMs sharing /www, including the entire push process.
 * The lock file lives in the shared certificate volume, and must never be deleted/replaced.
 */
public final class NginxWorkspace {
    private static final ReentrantLock LOCAL = new ReentrantLock(true);
    private static FileChannel channel;
    private static FileLock fileLock;

    private NginxWorkspace() {}

    public static Scope lock() {
        LOCAL.lock();
        boolean outer = LOCAL.getHoldCount() == 1;
        try {
            if (outer) {
                Path path = Path.of(System.getProperty("v7.nginx.workspaceLock", "/www/certs/.nginx-workspace.lock"));
                Files.createDirectories(path.toAbsolutePath().getParent());
                channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                fileLock = channel.lock();
            }
            // Keep file mutations isolated until the surrounding database transaction commits.
            boolean deferred = outer && TransactionSynchronizationManager.isSynchronizationActive();
            if (deferred) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        release();
                    }
                });
            }
            return new Scope(deferred);
        } catch (IOException | RuntimeException e) {
            release();
            throw new IllegalStateException("无法锁定 nginx 工作区", e);
        }
    }

    private static void release() {
        try {
            if (LOCAL.getHoldCount() == 1) {
                try {
                    if (fileLock != null) fileLock.release();
                } finally {
                    fileLock = null;
                    if (channel != null) channel.close();
                    channel = null;
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("释放 nginx 工作区锁失败", e);
        } finally {
            LOCAL.unlock();
        }
    }

    public static final class Scope implements AutoCloseable {
        private final boolean deferred;
        private boolean closed;

        private Scope(boolean deferred) { this.deferred = deferred; }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                if (!deferred) release();
            }
        }
    }
}
