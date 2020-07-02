package cz.isfgroup.sslspisumdatabox;

import cz.isfgroup.sslspisumdatabox.databox.DataboxCredentials;
import cz.isfgroup.sslspisumdatabox.processor.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RequiredArgsConstructor
@RestController
public class CacheController {

    private final CacheManager cacheManager;
    private final DataboxCredentials databoxCredentials;

    @DeleteMapping(value = "caches")
    public void clearCache() {
        cacheManager.getCacheNames().stream()
            .map(cacheManager::getCache)
            .filter(Objects::nonNull)
            .forEach(Cache::clear);
        databoxCredentials.init();
        UserService.clearUserTimestamps();
    }
}
