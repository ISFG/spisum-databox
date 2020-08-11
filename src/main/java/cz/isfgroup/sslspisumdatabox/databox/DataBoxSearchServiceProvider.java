package cz.isfgroup.sslspisumdatabox.databox;

import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DataBoxSearchServiceProvider {

    private final DataBoxManagerProvider dataBoxManagerProvider;

    @Cacheable(cacheNames = "searchServiceCache", sync = true)
    public DataBoxSearchService getDataBoxSearchService(String name) {
        return dataBoxManagerProvider.getDataBoxManager(name).getDataBoxSearchService();
    }
}
