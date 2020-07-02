package cz.isfgroup.sslspisumdatabox.downloader;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DownloaderStatus {

    boolean isRunning;
    int currentDownloaderThreadCount;
    int maximumDownloaderThreadCount;
    long completedDownloaderActions;
    int currentUploaderThreadCount;
    int maximumUploaderThreadCount;
    long completedUploaderActions;
}
