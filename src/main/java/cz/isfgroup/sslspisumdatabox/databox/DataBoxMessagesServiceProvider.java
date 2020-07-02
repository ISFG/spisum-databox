package cz.isfgroup.sslspisumdatabox.databox;

import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxMessagesService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class DataBoxMessagesServiceProvider {

    private final DataBoxManagerProvider dataBoxManagerProvider;

    @Cacheable(cacheNames = "messageServiceCache", sync = true)
    public DataBoxMessagesService getDataBoxMessagesService(String id) {
        return dataBoxManagerProvider.getDataBoxManager(id).getDataBoxMessagesService();
    }

}
