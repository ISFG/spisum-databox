package cz.isfgroup.sslspisumdatabox;

import cz.isfgroup.sslspisumdatabox.downloader.DownloaderStatus;
import cz.isfgroup.sslspisumdatabox.processor.DataboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class StatusController {

    private final DataboxService databoxService;

    @GetMapping("context")
    public DownloaderStatus status() {
        return DownloaderStatus.builder()
                .isRunning(databoxService.getRunningDownloadTasks() + databoxService.getRunningUploadTasks() > 0)
                .currentDownloaderThreadCount(databoxService.getRunningDownloadTasks())
                .maximumDownloaderThreadCount(databoxService.getMaximumDownloadTasks())
                .completedDownloaderActions(databoxService.getCompletedDownloadTasks())
                .currentUploaderThreadCount(databoxService.getRunningUploadTasks())
                .maximumUploaderThreadCount(databoxService.getMaximumUploadTasks())
                .completedUploaderActions(databoxService.getCompletedUploadTasks())
                .build();
    }


}