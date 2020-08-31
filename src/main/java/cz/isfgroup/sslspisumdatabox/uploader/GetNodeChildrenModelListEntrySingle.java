package cz.isfgroup.sslspisumdatabox.uploader;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetNodeChildrenModelListEntrySingle {

    private String id;
    private Map<String, String> properties;

}
