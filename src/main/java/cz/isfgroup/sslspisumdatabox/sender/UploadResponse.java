package cz.isfgroup.sslspisumdatabox.sender;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UploadResponse {

    @JsonProperty("IsSuccessfullySent")
    boolean isSuccessfullySent;

    @JsonProperty("MessageId")
    String messageId;

    @JsonProperty("Exception")
    String exception;

}
