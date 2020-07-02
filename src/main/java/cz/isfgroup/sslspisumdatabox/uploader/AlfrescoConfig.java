package cz.isfgroup.sslspisumdatabox.uploader;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Component
public class AlfrescoConfig {

    @Value("${alfresco.repository.user}")
    private String username;

    @Value("${alfresco.repository.pass}")
    private String password;

    @Value("${alfresco.repository.url}")
    private String serverUrl;

    private HttpHeaders multipartHeaders = new HttpHeaders();
    private HttpHeaders jsonHeaders = new HttpHeaders();

    @PostConstruct
    public void setHttpHeaders() {
        String auth = username + ":" + password;
        byte[] encodedAuth = Base64.encodeBase64(
            auth.getBytes(StandardCharsets.US_ASCII));
        String authHeader = "Basic " + new String(encodedAuth);

        multipartHeaders.set("Authorization", authHeader);
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);

        jsonHeaders.set("Authorization", authHeader);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
    }

    public HttpHeaders getMultipartHeaders() {
        return multipartHeaders;
    }

    public HttpHeaders getJsonHeaders() {
        return jsonHeaders;
    }

    public String getServerUrl() {
        return serverUrl;
    }

}
