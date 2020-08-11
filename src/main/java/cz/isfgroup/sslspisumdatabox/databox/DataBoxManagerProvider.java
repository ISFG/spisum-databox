package cz.isfgroup.sslspisumdatabox.databox;

import cz.abclinuxu.datoveschranky.common.Config;
import cz.abclinuxu.datoveschranky.common.DataBoxEnvironment;
import cz.abclinuxu.datoveschranky.impl.BasicAuthentication;
import cz.abclinuxu.datoveschranky.impl.DataBoxManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Slf4j
@Service
public class DataBoxManagerProvider {

    private final DataboxCredentials databoxCredentials;

    @Value("${environment}")
    private DataBoxEnvironment environment;

    @Cacheable(cacheNames = "databoxManagerCache", sync = true)
    public DataBoxManager getDataBoxManager(String username) {
        Config config = new Config(environment);
        return databoxCredentials.getUsernamePasswords().stream()
            .filter(t -> username.equals(t.getUsername()))
            .findFirst()
            .map(t -> new DataBoxManager(config, new BasicAuthentication(config, username, t.getPassword())))
            .orElseThrow();
    }

}
