package cz.isfgroup.sslspisumdatabox.databox;

import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DataBoxDownloadServiceProvider {

    private final DataBoxManagerProvider dataBoxManagerProvider;

    @Cacheable(cacheNames = "downloadServiceCache", sync = true)
    public DataBoxDownloadService getDataBoxDownloadService(String id) {
        return dataBoxManagerProvider.getDataBoxManager(id).getDataBoxDownloadService();
    }
}
