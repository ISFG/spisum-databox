package cz.isfgroup.sslspisumdatabox.databox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.isfgroup.sslspisumdatabox.DataboxException;
import cz.isfgroup.sslspisumdatabox.downloader.UsernamePasswordConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class DataboxCredentials {

    @Value("${credentials.file}")
    private String jsonConfigFile;

    private final ObjectMapper objectMapper;

    private List<UsernamePasswordConfig> usernamePasswords = Collections.emptyList();

    @PostConstruct
    public void init() {
        try {
            File file = ResourceUtils.getFile(jsonConfigFile);
                usernamePasswords = objectMapper.readValue(file, new TypeReference<>() {
                });
        } catch (FileNotFoundException e) {
            throw new DataboxException(String.format("Cannot open credentials file: %s", jsonConfigFile), e);
        } catch (IOException e) {
            throw new DataboxException(String.format("File %s could not be read", jsonConfigFile), e);
        }
    }

    public List<UsernamePasswordConfig> getUsernamePasswords() {
        return usernamePasswords;
    }
}