package cz.isfgroup.sslspisumdatabox.processor;

import cz.isfgroup.sslspisumdatabox.DataboxException;
import cz.isfgroup.sslspisumdatabox.downloader.DownloadResult;
import cz.isfgroup.sslspisumdatabox.downloader.DownloaderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class AttachmentService {

    private final DownloaderService downloaderService;
    private final UserService userService;

    @Async
    public CompletableFuture<DownloadResult> getAttachmentCount() throws InterruptedException, ExecutionException {
        List<User> users = userService.getAllUsers();
        List<CompletableFuture<DownloadResult>> resultCounts = users.stream()
            .map(downloaderService::download)
            .collect(Collectors.toList());
        CompletableFuture.allOf(resultCounts
            .toArray(size -> new CompletableFuture[users.size()]))
            .get();
        DownloadResult result = resultCounts.stream()
            .map(resultFuture -> {
                try {
                    return resultFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    // this will not happen as all futures are already finished
                    throw new DataboxException(e);
                }
            })
            .filter(Objects::nonNull)
            .reduce(new DownloadResult(),
                (a, b) -> DownloadResult.builder().attachmentCount(a.getAttachmentCount() + b.getAttachmentCount()).build());
        return CompletableFuture.completedFuture(result);
    }

}
