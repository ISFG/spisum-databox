package cz.isfgroup.sslspisumdatabox.uploader;

import cz.isfgroup.sslspisumdatabox.downloader.EnvelopeData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
@Slf4j
@Service
public class AlfrescoService {

    private static final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final Pattern reservedCharacters = Pattern.compile("([\"\\<\\>\\|\\\\\\:\\&\\;\\?\\*\\|\"]+)");

    private final RestTemplate restTemplate;
    private final AlfrescoConfig alfrescoConfig;

    @Value("${download.folder:/tmp}")
    private String downloadFolder;

    @Async("uploaderExecutor")
    public CompletableFuture<GetNodeChildrenModelListEntry> moveFile(String localFileName, String folderId,
                                                                     EnvelopeData envelopeData) {
        Path localFile = Path.of(downloadFolder, escapeReservedCharacters(localFileName));
        log.info("Uploading file: {}", localFile);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("filedata", new FileSystemResource(localFile));

        if (envelopeData.getAttachmentCount() != null) {
            body.add("ssl:databoxAttachmentsCount", envelopeData.getAttachmentCount());
            body.add("ssl:databoxDeliveryDate", envelopeData.getDeliveryTime());
            body.add("ssl:databoxRecipient", envelopeData.getRecipientId());
            body.add("ssl:databoxRecipientName", envelopeData.getRecipientName());
            body.add("ssl:databoxRecipientDataBoxType", envelopeData.getRecipientType());
            body.add("ssl:databoxSender", envelopeData.getSenderId());
            body.add("ssl:databoxSenderName", envelopeData.getSenderName());
            body.add("ssl:databoxSenderDataBoxType", envelopeData.getSenderType());
            body.add("ssl:databoxSubject", envelopeData.getSubject());
        }
        body.add("ssl:databoxRecipientUid", envelopeData.getRecipientUsername());
        body.add("nodeType", envelopeData.getNodeType());
        body.add("name", String.format("%s-%s.%s", LocalDateTime.now().format(dateTimeFormatter), getShortUuid(),
            FilenameUtils.getExtension(localFileName)));
        body.add("ssl:fileName", localFileName);

        HttpEntity<MultiValueMap<String, Object>> requestEntity
            = new HttpEntity<>(body, alfrescoConfig.getMultipartHeaders());
        String url = String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s/children",
            alfrescoConfig.getServerUrl(),
            folderId);

        ResponseEntity<GetNodeChildrenModelListEntry> response = restTemplate.postForEntity(url, requestEntity,
            GetNodeChildrenModelListEntry.class);
        log.info("File uploaded {}", response);
        try {
            Files.delete(localFile);
        } catch (IOException e) {
            log.error("Cannot remove file: {}", localFile, e);
        }
        return CompletableFuture.completedFuture(response.getBody());
    }

    public String updateChild(String parent, ZfoChildrenMapping zfoChildrenMapping) {
        String url = String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s/secondary-children",
            alfrescoConfig.getServerUrl(),
            parent);
        HttpEntity<ZfoChildrenMapping> httpEntity = new HttpEntity<>(zfoChildrenMapping, alfrescoConfig.getJsonHeaders());
        ResponseEntity<String> response = restTemplate.postForEntity(url, httpEntity, String.class);

        log.info("Parent {} updated with mapping {}", parent, zfoChildrenMapping);
        return response.getBody();
    }

    public void deleteNode(String nodeId) {
        String url = String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s",
            alfrescoConfig.getServerUrl(),
            nodeId);
        HttpEntity<ZfoChildrenMapping> httpEntity = new HttpEntity<>(alfrescoConfig.getJsonHeaders());
        restTemplate.exchange(url, HttpMethod.DELETE, httpEntity, String.class);

        log.info("Deleted node: {}", nodeId);
    }

    public static String getShortUuid() {
        UUID uuid = UUID.randomUUID();
        long l = ByteBuffer.wrap(uuid.toString().getBytes()).getLong();
        return Long.toString(l, Character.MAX_RADIX);
    }

    private static String escapeReservedCharacters(String filename) {
        Matcher matcher = reservedCharacters.matcher(filename);
        if (matcher.find()) {
            String replaced = matcher.replaceAll("\\\\$1");
            log.info("Escaping file: '{}' as '{}'", filename, replaced);
            return replaced;
        } else {
            return filename;
        }

    }

}
