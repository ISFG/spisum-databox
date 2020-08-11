package cz.isfgroup.sslspisumdatabox.databox;

import cz.abclinuxu.datoveschranky.common.entities.DataBoxWithDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DataboxSearchService {

    private final DataBoxSearchServiceProvider dataBoxSearchServiceProvider;

    @Cacheable(cacheNames = "databoxManagerCache", sync = true)
    public DataBoxWithDetails getDatabox(String name, String id) {
        return dataBoxSearchServiceProvider.getDataBoxSearchService(name).findDataBoxByID(id);
    }
}
