package cz.isfgroup.sslspisumdatabox.downloader;

import org.springframework.util.MultiValueMap;

public interface EnvelopeDataEnhancer<T extends EnvelopeData> {

    void addProperties(MultiValueMap<String, Object> body, T envelopeData);
}
