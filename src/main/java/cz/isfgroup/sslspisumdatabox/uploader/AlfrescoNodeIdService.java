package cz.isfgroup.sslspisumdatabox.uploader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.isfgroup.sslspisumdatabox.PropertiesModel;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class AlfrescoNodeIdService {

    public static final String TIMESTAMP_TEXT = "ssl:timestampText";
    private final AlfrescoConfig alfrescoConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${alfresco.unprocessed.path}")
    private String unprocessedPath;

    @Cacheable(cacheNames = "nodeIdCache", sync = true)
    public String getUnprocessedNodeId() {
        String url = String.format(
                "%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/-root-/?skipCount=0&maxItems=1&orderBy=createdAt DESC&relativePath=%s",
                alfrescoConfig.getServerUrl(),
                unprocessedPath);
        HttpEntity<String> entity = new HttpEntity<>(alfrescoConfig.getJsonHeaders());

        ResponseEntity<GetNodeChildrenModelListEntry> response = restTemplate.exchange(url, HttpMethod.GET, entity,
                GetNodeChildrenModelListEntry.class);
        if (response.getBody() != null) {
            return response.getBody().getEntry().getId();
        } else {
            throw new RuntimeException(String.format("Cannot find nodeId from URL: %s", url));
        }

    }

    public Map<String, AlfrescoTimestamp> getUnprocessedNodeUserTimestamps(String nodeId) {
        String url = String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s",
            alfrescoConfig.getServerUrl(),
            nodeId);
        HttpEntity<String> entity = new HttpEntity<>(alfrescoConfig.getJsonHeaders());

        ResponseEntity<GetNodeChildrenModelListEntry> response = restTemplate.exchange(url, HttpMethod.GET, entity,
            GetNodeChildrenModelListEntry.class);
        if (response.getBody() != null) {
            try {
                Map<String, String> properties = response.getBody().getEntry().getProperties();
                if (properties != null) {
                    String timestampText = properties.get(TIMESTAMP_TEXT);
                    if (StringUtils.isNotBlank(timestampText)) {
                        return objectMapper.readValue(timestampText, new TypeReference<>() {
                        });
                    } else {
                        return new HashMap<>();
                    }
                } else {
                    return new HashMap<>();
                }
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Cannot parse user timestamps from Alfresco", e);
            }
        } else {
            throw new RuntimeException(String.format("Cannot find nodeId from URL: %s", url));
        }

    }

    public synchronized void addUnprocessedNodeUserTimestamps(String nodeId, String user, Long timestamp, Long messageTimestamp) {
        Map<String, AlfrescoTimestamp> timestamps = getUnprocessedNodeUserTimestamps(nodeId);
        if (timestamp == 0) {
            timestamps.put(user, null);
        } else {
            if (messageTimestamp == null) {
                messageTimestamp = timestamps.get(user).getTimestamp();
            }
            timestamps.put(user, AlfrescoTimestamp.builder().timestamp(messageTimestamp).downloadTimestamp(timestamp).build());
        }

        String url = String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s",
            alfrescoConfig.getServerUrl(),
            nodeId);
        Map<String, String> propertyMap = new HashMap<>();
        try {
            propertyMap.put(TIMESTAMP_TEXT, objectMapper.writeValueAsString(timestamps));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot parse text", e);
        }
        HttpEntity<PropertiesModel> entity = new HttpEntity<>(PropertiesModel.builder().properties(propertyMap).build(),
                alfrescoConfig.getJsonHeaders());

        restTemplate.exchange(url, HttpMethod.PUT, entity, GetNodeChildrenModelListEntry.class);

    }
}