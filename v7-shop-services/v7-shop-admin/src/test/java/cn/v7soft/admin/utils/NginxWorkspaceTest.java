package cn.v7soft.admin.utils;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.channels.FileChannel;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class NginxWorkspaceTest {
    @Test
    void nestedScopesKeepOperatingSystemLockUntilOutermostClose() throws Exception {
        Path lock = Path.of(System.getProperty("v7.nginx.workspaceLock"));
        try (var outer = NginxWorkspace.lock()) {
            try (var inner = NginxWorkspace.lock()) { /* nested publication */ }
            try (var other = FileChannel.open(lock, StandardOpenOption.WRITE)) {
                assertThrows(OverlappingFileLockException.class, other::tryLock);
            }
        }
        try (var other = FileChannel.open(lock, StandardOpenOption.WRITE); var acquired = other.tryLock()) {
            assertNotNull(acquired);
        }
    }

    @Test
    void keepsLockUntilDatabaseTransactionCompletes() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        TransactionSynchronizationManager.initSynchronization();
        try {
            try (var ignored = NginxWorkspace.lock()) { /* method returned before commit */ }
            CountDownLatch started = new CountDownLatch(1);
            Future<?> writer = executor.submit(() -> {
                started.countDown();
                try (var ignored = NginxWorkspace.lock()) { /* another writer */ }
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> writer.get(150, TimeUnit.MILLISECONDS));
            for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
            }
            TransactionSynchronizationManager.clearSynchronization();
            writer.get(5, TimeUnit.SECONDS);
        } finally {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                for (var sync : TransactionSynchronizationManager.getSynchronizations()) {
                    sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
                }
                TransactionSynchronizationManager.clearSynchronization();
            }
            executor.shutdownNow();
        }
    }
}
