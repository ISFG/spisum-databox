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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

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
        setDefaultKeyStore(config);
        return databoxCredentials.getUsernamePasswords().stream()
            .filter(t -> username.equals(t.getUsername()))
            .findFirst()
            .map(t -> new DataBoxManager(config, new BasicAuthentication(config, username, t.getPassword())))
            .orElseThrow();
    }

    /**
     * The isds library is old, undocumented and uses old certificates from the time when databox's certificates were not trusted
     * by operating systems. They currently use trusted certificates and those old hardcoded certificates do not work anymore.
     *
     * @param config default system certificates will replace the hardcoded values from library
     */
    private void setDefaultKeyStore(Config config) {
        // default Java path
        String filename = System.getProperty("java.home") + "/lib/security/cacerts".replace('/', File.separatorChar);
        try (FileInputStream is = new FileInputStream(filename)) {
            Field keyStore = config.getClass().getDeclaredField("keyStore");
            keyStore.setAccessible(true);
            // Load the default JDK's cacerts keystore file
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            // default Java password
            String password = "changeit";
            ks.load(is, password.toCharArray());
            // reset the value
            keyStore.set(config, ks);
        } catch (NoSuchFieldException | IllegalAccessException | KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            log.error("Cannot set default keystore because of a change in the underlying dependency", e);
        }
    }
}
