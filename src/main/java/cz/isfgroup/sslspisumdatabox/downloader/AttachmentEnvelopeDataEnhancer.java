package cz.isfgroup.sslspisumdatabox.downloader;

import org.springframework.util.MultiValueMap;

public class AttachmentEnvelopeDataEnhancer implements EnvelopeDataEnhancer<AttachmentEnvelopeData> {

    @Override
    public void addProperties(MultiValueMap<String, Object> body, AttachmentEnvelopeData envelopeData) {
        body.add("nodeType", "ssl:component");
        body.add("ssl:pid", String.format("%s/%s", envelopeData.getZfoPid(), envelopeData.getAttachmentNumber()));
        body.add("ssl:databoxRecipientUid", envelopeData.getRecipientUsername());
    }
}
