package cz.isfgroup.sslspisumdatabox;

import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class UserEntity {

    String name;
    String username;

}
