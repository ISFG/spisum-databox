package cz.isfgroup.sslspisumdatabox.databox;

import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DataBoxUploadServiceProvider {

    private final DataBoxManagerProvider dataBoxManagerProvider;

    @Cacheable(cacheNames = "uploadServiceCache", sync = true)
    public DataBoxUploadService getDataBoxUploadService(String id) {
        return dataBoxManagerProvider.getDataBoxManager(id).getDataBoxUploadService();
    }
}
