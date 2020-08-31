package cz.isfgroup.sslspisumdatabox.processor;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@AllArgsConstructor
@Builder
@Data
@NoArgsConstructor
public class Status {

    private long jobId;
    private boolean running;
    private long newMessageCount;
    private List<String> stackTrace;
}
