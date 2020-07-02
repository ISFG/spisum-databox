package cz.isfgroup.sslspisumdatabox.processor;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class DownloaderController {

    private final DataboxService databoxService;

    @PostMapping("refresh")
    public String download() {
        return String.valueOf(databoxService.processDataboxes().getJobId());
    }

    @GetMapping("status")
    public ResponseEntity<Status> status(@RequestParam long id) {
        Status status = databoxService.getStatus(id);
        if (status == null) {
            return ResponseEntity.notFound().build();
        } else if (status.getStackTrace() != null && !status.getStackTrace().isEmpty()) {
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(status);
        } else {
            return ResponseEntity.ok(status);
        }
    }

}
