package cz.isfgroup.sslspisumdatabox.downloader;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class EnvelopeData implements Serializable {

    private String nodeType;
    private Integer attachmentCount;
    private String deliveryTime;
    private String recipientId;
    private String recipientName;
    private String recipientUsername;
    private String recipientType;
    private String senderId;
    private String senderName;
    private String senderType;
    private String subject;

}
