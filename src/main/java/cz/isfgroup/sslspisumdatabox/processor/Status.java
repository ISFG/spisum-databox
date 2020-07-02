package cz.isfgroup.sslspisumdatabox.processor;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class Status {

    long jobId;
    boolean running;
    long newMessageCount;
    List<String> stackTrace;
}
