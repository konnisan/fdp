package com.delivery.fdp.service;

import com.delivery.fdp.config.ManagedRuntimeProperties;
import com.delivery.fdp.repository.ManagedProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class AtomicManagedArtifactMaterializeService {
    private static final Logger log = LoggerFactory.getLogger(AtomicManagedArtifactMaterializeService.class);

    private final ManagedArtifactMaterializeService delegate;
    private final ManagedRuntimeProperties managed;
    private final ManagedProjectRepository repository;
    private final TransactionTemplate transactions;
    private final ConcurrentHashMap<Long, ReentrantLock> projectLocks = new ConcurrentHashMap<>();

    public AtomicManagedArtifactMaterializeService(ManagedArtifactMaterializeService delegate,
                                                   ManagedRuntimeProperties managed,
                                                   ManagedProjectRepository repository,
                                                   TransactionTemplate transactions) {
        this.delegate = delegate;
        this.managed = managed;
        this.repository = repository;
        this.transactions = transactions;
    }

    public Map<String, Object> materialize(Long projectId,
                                           ManagedArtifactMaterializeService.MaterializeRequest request) {
        ReentrantLock lock = projectLocks.computeIfAbsent(projectId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new IllegalStateException("当前项目已有下载/解压任务正在执行，请稍后重试");
        }
        try {
            Map<String, Object> result = transactions.execute(status -> materializeInTransaction(projectId, request, status));
            if (result == null) throw new IllegalStateException("制品 materialize 事务未返回结果");
            return result;
        } catch (RuntimeException error) {
            safeUpdateError(projectId, error);
            throw error;
        } finally {
            lock.unlock();
        }
    }

    private Map<String, Object> materializeInTransaction(
            Long projectId,
            ManagedArtifactMaterializeService.MaterializeRequest request,
            org.springframework.transaction.TransactionStatus status) {
        Path root = projectRoot(projectId);
        Path current = root.resolve("current");
        Path backup = root.resolve(".current-transaction-backup-" + System.nanoTime());
        CurrentSnapshot snapshot = snapshotCurrent(current, backup);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int completionStatus) {
                if (completionStatus == TransactionSynchronization.STATUS_COMMITTED) {
                    deleteQuietly(snapshot.backup());
                } else {
                    restoreQuietly(snapshot);
                }
            }
        });

        try {
            return delegate.materialize(projectId, request);
        } catch (RuntimeException error) {
            status.setRollbackOnly();
            throw error;
        }
    }

    private CurrentSnapshot snapshotCurrent(Path current, Path backup) {
        try {
            Files.createDirectories(current.getParent());
            boolean hadPreviousCurrent = Files.exists(current);
            if (hadPreviousCurrent) copyRecursively(current, backup);
            return new CurrentSnapshot(current, backup, hadPreviousCurrent);
        } catch (IOException error) {
            try { deleteRecursively(backup); } catch (Exception ignored) {}
            throw new IllegalStateException("备份旧 current 目录失败: " + message(error), error);
        }
    }

    private void copyRecursively(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            for (Path item : stream.toList()) {
                Path relative = source.relativize(item);
                Path destination = target.resolve(relative);
                if (Files.isDirectory(item)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(item, destination,
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private void restoreQuietly(CurrentSnapshot snapshot) {
        try {
            if (Files.exists(snapshot.current())) deleteRecursively(snapshot.current());
            if (snapshot.hadPreviousCurrent() && Files.exists(snapshot.backup())) {
                Files.move(snapshot.backup(), snapshot.current(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception error) {
            log.error("Failed to restore current directory after materialize rollback. current={}, backup={}",
                    snapshot.current(), snapshot.backup(), error);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            deleteRecursively(path);
        } catch (Exception error) {
            log.warn("Materialize committed but backup cleanup failed: {}", path, error);
        }
    }

    private Path projectRoot(Long id) {
        Path root = Path.of(managed.getProjectRoot()).toAbsolutePath().normalize();
        Path project = root.resolve(String.valueOf(id)).normalize();
        if (!project.startsWith(root)) throw new IllegalStateException("项目目录非法");
        return project;
    }

    private void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) return;
        try (var stream = Files.walk(path)) {
            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(item);
        }
    }

    private void safeUpdateError(Long projectId, Throwable error) {
        try {
            repository.updateError(projectId, message(error));
        } catch (Exception ignored) {
        }
    }

    private String message(Throwable error) {
        return error == null || error.getMessage() == null ? String.valueOf(error) : error.getMessage();
    }

    private record CurrentSnapshot(Path current, Path backup, boolean hadPreviousCurrent) {}
}
