package cz.isfgroup.sslspisumdatabox.uploader;

import lombok.Data;

import java.util.Map;

@Data
public class GetNodeChildrenModelListEntrySingle {

    private String id;
    private Map<String, String> properties;

}
