package cz.isfgroup.sslspisumdatabox;

import cz.isfgroup.sslspisumdatabox.databox.DataboxCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
public class AccountController {

    private final DataboxCredentials databoxCredentials;

    @GetMapping("accounts")
    public List<UserEntity> accounts() {
        return databoxCredentials.getUsernamePasswords().stream()
            .map(t -> UserEntity.builder()
                .name(t.getName())
                .username(t.getUsername())
                .id(t.getId())
                .build())
            .collect(Collectors.toList());
    }
}
