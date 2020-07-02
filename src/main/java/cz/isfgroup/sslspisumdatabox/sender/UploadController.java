package cz.isfgroup.sslspisumdatabox.sender;

import cz.abclinuxu.datoveschranky.common.entities.content.ByteContent;
import cz.isfgroup.sslspisumdatabox.processor.User;
import cz.isfgroup.sslspisumdatabox.processor.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
public class UploadController {

    private final UploadService uploadService;
    private final UserService userService;

    @PostMapping(value = "/databox-message", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> submit(@RequestParam("files") List<MultipartFile> files,
                                                 @RequestParam String body,
                                                 @RequestParam String subject,
                                                 @RequestParam String recipientId,
                                                 @RequestParam String senderName) {
        if (userService.getAllUsers().stream()
            .map(User::getName)
            .anyMatch(t -> t.equals(senderName))) {
            log.info("Received files: '{}', subject: '{}', body: '{}', recipientId: '{}' and senderName: '{}'",
                files.stream()
                    .map(MultipartFile::getOriginalFilename)
                    .collect(Collectors.toList()),
                subject,
                body,
                recipientId,
                senderName);
            String messageId = uploadService.send(senderName, recipientId, body, subject, files.stream()
                .map(t -> {
                    try {
                        return UploadedAttachment.builder()
                            .mimeType(t.getContentType())
                            .originalName(t.getOriginalFilename())
                            .content(new ByteContent(t.getBytes()))
                            .build();
                    } catch (IOException e) {
                        log.error("Cannot get bytes from the file: {}", t.getOriginalFilename(), e);
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList()));
            log.info("Message '{}' sent to the recipientId: {}", subject, recipientId);
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(UploadResponse.builder().isSuccessfullySent(true).messageId(messageId).build());
        } else {
            log.info("Sender's name '{}' not found in local credentials", senderName);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(UploadResponse.builder().isSuccessfullySent(false).exception("User not found").build());
        }
    }
}
