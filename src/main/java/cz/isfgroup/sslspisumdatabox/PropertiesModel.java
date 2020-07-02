package cz.isfgroup.sslspisumdatabox;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Builder
@Data
public class PropertiesModel {
    private Map<String, String> properties;
}
