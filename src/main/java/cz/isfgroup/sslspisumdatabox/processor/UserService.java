package cz.isfgroup.sslspisumdatabox.processor;

import cz.isfgroup.sslspisumdatabox.databox.DataboxCredentials;
import cz.isfgroup.sslspisumdatabox.downloader.UsernamePasswordConfig;
import cz.isfgroup.sslspisumdatabox.uploader.AlfrescoNodeIdService;
import cz.isfgroup.sslspisumdatabox.uploader.AlfrescoTimestamp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    private final DataboxCredentials databoxCredentials;
    private final AlfrescoNodeIdService alfrescoNodeIdService;

    private static final Map<String, Long> userTimestamp = new ConcurrentHashMap<>();

    @Value("${download.initial.history.days}")
    private int initialHistoryDays;

    public List<User> getAllUsers() {
        return databoxCredentials.getUsernamePasswords().stream()
            .map(UsernamePasswordConfig::getUsername)
            .map(t -> User.builder()
                .name(t)
                .timestamp(getUserTimestamp(t))
                .build())
            .collect(Collectors.toList());
    }

    public Long getUserTimestamp(String id) {
        return getPreviousTimestamp(id)
            .orElseGet(() -> {
                long newTimestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(initialHistoryDays);
                userTimestamp.put(id, newTimestamp);
                return newTimestamp;
            });
    }

    public Optional<Long> getPreviousTimestamp(String id) {
        return Optional.ofNullable(Optional.ofNullable(userTimestamp.get(id))
            .orElseGet(() -> Optional.ofNullable(
                alfrescoNodeIdService.getUnprocessedNodeUserTimestamps(alfrescoNodeIdService.getUnprocessedNodeId()).get(
                    id))
                .map(AlfrescoTimestamp::getDownloadTimestamp)
                .orElse(null)));
    }

    public void setUserTimestamp(String id, Long realTimestamp, Long messageTimestamp) {
        userTimestamp.put(id, realTimestamp);
        alfrescoNodeIdService.addUnprocessedNodeUserTimestamps(alfrescoNodeIdService.getUnprocessedNodeId(), id, realTimestamp,
            messageTimestamp);
    }

    public static void clearUserTimestamps() {
        userTimestamp.clear();
    }

}
