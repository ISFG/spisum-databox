package cz.isfgroup.sslspisumdatabox.sender;

import cz.abclinuxu.datoveschranky.common.entities.Attachment;
import cz.abclinuxu.datoveschranky.common.entities.DataBox;
import cz.abclinuxu.datoveschranky.common.entities.Message;
import cz.abclinuxu.datoveschranky.common.entities.MessageEnvelope;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxUploadServiceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Slf4j
@Service
public class UploadService {

    private final DataBoxUploadServiceProvider dataBoxUploadServiceProvider;

    public String send(String sender, String recipient, String content, String subject, List<UploadedAttachment> attachments) {
        MessageEnvelope messageEnvelope = new MessageEnvelope();
        messageEnvelope.setRecipient(new DataBox(recipient));
        messageEnvelope.setSender(new DataBox(sender));
        messageEnvelope.setAnnotation(subject);

        Message message = new Message(messageEnvelope, null, null, attachments.stream()
            .map(t -> {
                Attachment attachment = new Attachment();
                attachment.setDescription(t.getOriginalName());
                attachment.setMetaType("main");
                attachment.setMimeType(t.getMimeType());
                attachment.setContents(t.getContent());
                return attachment;
            })
            .collect(Collectors.toList()));
        dataBoxUploadServiceProvider.getDataBoxUploadService(sender).sendMessage(message);
        return message.getEnvelope().getMessageID();
    }
}
