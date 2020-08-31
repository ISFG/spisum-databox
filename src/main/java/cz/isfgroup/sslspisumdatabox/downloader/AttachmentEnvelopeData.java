package cz.isfgroup.sslspisumdatabox.downloader;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class AttachmentEnvelopeData implements EnvelopeData, Serializable {

    private String recipientUsername;
    private String zfoPid;
    private long attachmentNumber;

}
