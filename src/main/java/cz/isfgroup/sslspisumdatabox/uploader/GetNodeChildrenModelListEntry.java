package cz.isfgroup.sslspisumdatabox.uploader;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetNodeChildrenModelListEntry {

    private GetNodeChildrenModelListEntrySingle entry;
}
