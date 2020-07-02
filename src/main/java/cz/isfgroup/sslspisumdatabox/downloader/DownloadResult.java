package cz.isfgroup.sslspisumdatabox.downloader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadResult {

    private long attachmentCount;
    @Builder.Default
    private List<Throwable> error = new ArrayList<>();
}
