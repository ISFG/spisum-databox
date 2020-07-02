package cz.isfgroup.sslspisumdatabox.uploader;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ZfoChildrenMapping {

    private String childId;
    @Builder.Default
    private String assocType = "ssl:databoxAttachments";

}
