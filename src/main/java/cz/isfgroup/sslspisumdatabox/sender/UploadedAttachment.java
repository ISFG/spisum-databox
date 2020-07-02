package cz.isfgroup.sslspisumdatabox.sender;

import cz.abclinuxu.datoveschranky.common.entities.content.Content;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class UploadedAttachment {

    String originalName;
    String mimeType;
    Content content;

}
