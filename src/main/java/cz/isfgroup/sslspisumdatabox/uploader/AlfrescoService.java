package cz.isfgroup.sslspisumdatabox.uploader;

import cz.isfgroup.sslspisumdatabox.downloader.AttachmentEnvelopeData;
import cz.isfgroup.sslspisumdatabox.downloader.AttachmentEnvelopeDataEnhancer;
import cz.isfgroup.sslspisumdatabox.downloader.EnvelopeData;
import cz.isfgroup.sslspisumdatabox.downloader.EnvelopeDataEnhancer;
import cz.isfgroup.sslspisumdatabox.downloader.ZfoEnvelopeData;
import cz.isfgroup.sslspisumdatabox.downloader.ZfoEnvelopeDataEnhancer;
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

    private static final ZfoEnvelopeDataEnhancer zfoEnvelopeDataEnhancer = new ZfoEnvelopeDataEnhancer();
    private static final AttachmentEnvelopeDataEnhancer attachmentEnvelopeDataEnhancer = new AttachmentEnvelopeDataEnhancer();

    private final RestTemplate restTemplate;
    private final AlfrescoConfig alfrescoConfig;
    private final AlfrescoNodeService alfrescoNodeService;

    @Value("${download.folder:/tmp}")
    private String downloadFolder;

    @Async("uploaderExecutor")
    public CompletableFuture<GetNodeChildrenModelListEntry> moveFileToUnprocessed(String localFileName,
                                                                                  ZfoEnvelopeData envelopeData) {
        return moveFileToUnprocessed(localFileName, envelopeData, zfoEnvelopeDataEnhancer);
    }

    @Async("uploaderExecutor")
    public CompletableFuture<GetNodeChildrenModelListEntry> moveFileToUnprocessed(String localFileName,
                                                                                  AttachmentEnvelopeData envelopeData) {
        return moveFileToUnprocessed(localFileName, envelopeData, attachmentEnvelopeDataEnhancer);
    }

    private <T extends EnvelopeData> CompletableFuture<GetNodeChildrenModelListEntry> moveFileToUnprocessed(String localFileName,
                                                                                                            T envelopeData,
                                                                                                            EnvelopeDataEnhancer<T> enhancer) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        Path localFile = addCommonProperties(localFileName, body);
        enhancer.addProperties(body, envelopeData);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, alfrescoConfig.getMultipartHeaders());
        String url = String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s/children",
            alfrescoConfig.getServerUrl(),
            alfrescoNodeService.getUnprocessedNodeId());

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

    private Path addCommonProperties(String localFileName, MultiValueMap<String, Object> body) {
        Path localFile = Path.of(downloadFolder, escapeReservedCharacters(localFileName));
        log.info("Uploading file: {}", localFile);
        body.add("filedata", new FileSystemResource(localFile));
        body.add("name", String.format("%s-%s.%s", LocalDateTime.now().format(dateTimeFormatter), getShortUuid(),
            FilenameUtils.getExtension(localFileName)));
        body.add("ssl:fileName", localFileName);
        return localFile;
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
