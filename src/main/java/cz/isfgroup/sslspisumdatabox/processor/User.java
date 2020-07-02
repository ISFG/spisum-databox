package cz.isfgroup.sslspisumdatabox.processor;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class User {

    private String name;
    private Long timestamp;
}
