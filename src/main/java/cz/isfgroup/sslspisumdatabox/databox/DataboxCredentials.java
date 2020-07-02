package cz.isfgroup.sslspisumdatabox.databox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.isfgroup.sslspisumdatabox.downloader.UsernamePasswordConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class DataboxCredentials {

    @Value("${credentials.file:/tmp/credentials.json}")
    private String jsonConfigFile;

    private final ObjectMapper objectMapper;

    private List<UsernamePasswordConfig> usernamePasswords = Collections.emptyList();

    @PostConstruct
    public void init() {
        File file = new File(jsonConfigFile);
        if (file.exists()) {
            try {
                usernamePasswords = objectMapper.readValue(file, new TypeReference<>() {
                });
            } catch (IOException e) {
                log.error("File {} could not be read", jsonConfigFile);
            }
        } else {
            log.warn("File not found: {}", jsonConfigFile);
        }
    }

    public List<UsernamePasswordConfig> getUsernamePasswords() {
        return usernamePasswords;
    }
}