package com.skatehub.service;

import com.skatehub.pojo.admin.NewsSyncNowRequest;
import com.skatehub.pojo.admin.NewsSyncTriggerResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsSyncTaskService {

    private final NewsFetchService newsFetchService;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "news-sync-task");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicReference<NewsSyncTriggerResponse> latestStatus = new AtomicReference<>(
            NewsSyncTriggerResponse.builder()
                    .taskId("")
                    .running(false)
                    .completed(false)
                    .enabled(true)
                    .message("暂无正在执行的资讯同步任务")
                    .build()
    );

    public synchronized NewsSyncTriggerResponse startAsync(NewsSyncNowRequest request) {
        NewsSyncTriggerResponse current = latestStatus.get();
        if (current != null && current.isRunning()) {
            return current;
        }

        String taskId = UUID.randomUUID().toString();
        NewsSyncTriggerResponse started = NewsSyncTriggerResponse.builder()
                .taskId(taskId)
                .running(true)
                .completed(false)
                .enabled(true)
                .dryRun(request != null && Boolean.TRUE.equals(request.getDryRun()))
                .message("资讯同步任务已启动，正在后台执行")
                .build();
        latestStatus.set(started);

        CompletableFuture.runAsync(() -> runTask(taskId, request == null ? new NewsSyncNowRequest() : request), executor);
        return started;
    }

    public NewsSyncTriggerResponse currentStatus() {
        return latestStatus.get();
    }

    private void runTask(String taskId, NewsSyncNowRequest request) {
        try {
            NewsSyncTriggerResponse result = newsFetchService.syncNow(request);
            result.setTaskId(taskId);
            result.setRunning(false);
            result.setCompleted(true);
            latestStatus.set(result);
        } catch (Exception exception) {
            log.warn("news sync async task failed, taskId={}", taskId, exception);
            latestStatus.set(NewsSyncTriggerResponse.builder()
                    .taskId(taskId)
                    .running(false)
                    .completed(true)
                    .enabled(true)
                    .dryRun(request != null && Boolean.TRUE.equals(request.getDryRun()))
                    .message("资讯同步失败: " + exception.getMessage())
                    .failedCount(1)
                    .build());
        }
    }
}
