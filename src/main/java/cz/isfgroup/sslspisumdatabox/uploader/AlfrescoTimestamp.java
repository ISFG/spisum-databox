package cz.isfgroup.sslspisumdatabox.uploader;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class AlfrescoTimestamp {

    private Long downloadTimestamp;
    private Long timestamp;

}
