package cz.isfgroup.sslspisumdatabox.processor;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class DataboxService {

    private final ThreadPoolExecutor databoxExecutor;
    private final ThreadPoolExecutor uploaderExecutor;
    private final AttachmentService attachmentService;

    private static final LoadingCache<Long, Status> jobs = Caffeine.newBuilder()
        .maximumSize(10000)
        .build(key -> null);
    private static final AtomicLong CURRENT_JOB_ID = new AtomicLong(0);

    @Scheduled(fixedDelayString = "${downloader.delay.ms:60000}", cron = "${downloader.delay.cron:}")
    public Status processDataboxes() {
        long currentJobId = CURRENT_JOB_ID.get();
        if ((getRunningDownloadTasks() == 0) && (getRunningUploadTasks() == 0) && (!Optional.ofNullable(
            jobs.get(currentJobId)).map(Status::isRunning).orElse(false))) {
            jobs.put(currentJobId, Status.builder().jobId(currentJobId).newMessageCount(0).running(true).build());
            try {
                attachmentService.getAttachmentCount().thenAccept(result -> {
                    List<String> stackTraces = result.getError() != null ? result.getError().stream()
                        .map(ExceptionUtils::getStackTrace)
                        .collect(Collectors.toList()) : Collections.emptyList();
                    Status status = Status.builder()
                        .newMessageCount(result.getAttachmentCount())
                        .stackTrace(stackTraces.isEmpty() ? null : stackTraces)
                        .running(false)
                        .jobId(currentJobId)
                        .build();
                    jobs.put(currentJobId, status);
                    CURRENT_JOB_ID.getAndAdd(1);
                }).exceptionally(e -> {
                    log.error("Caught error during postprocessing", e);
                    processExceptionStatus(currentJobId, e);
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                processExceptionStatus(currentJobId, e);
            } catch (ExecutionException e) {
                log.error("Downloading databoxes failed with error", e);
                processExceptionStatus(currentJobId, e);
            }
        }
        return jobs.get(currentJobId);
    }

    private void processExceptionStatus(long currentJobId, Throwable e) {
        jobs.put(currentJobId, Status.builder()
            .jobId(currentJobId)
            .running(false)
            .stackTrace(Collections.singletonList(ExceptionUtils.getStackTrace(e)))
            .build());
    }

    public Status getStatus(long id) {
        return jobs.get(id);
    }

    public int getRunningDownloadTasks() {
        return databoxExecutor.getActiveCount();
    }

    public int getMaximumDownloadTasks() {
        return databoxExecutor.getMaximumPoolSize();
    }

    public long getCompletedDownloadTasks() {
        return databoxExecutor.getCompletedTaskCount();
    }

    public int getRunningUploadTasks() {
        return uploaderExecutor.getActiveCount();
    }

    public int getMaximumUploadTasks() {
        return uploaderExecutor.getMaximumPoolSize();
    }

    public long getCompletedUploadTasks() {
        return uploaderExecutor.getCompletedTaskCount();
    }
}
