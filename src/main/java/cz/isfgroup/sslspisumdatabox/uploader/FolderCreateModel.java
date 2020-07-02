package cz.isfgroup.sslspisumdatabox.uploader;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FolderCreateModel {

    private String name;
    private final String nodeType = "cm:folder";

}
