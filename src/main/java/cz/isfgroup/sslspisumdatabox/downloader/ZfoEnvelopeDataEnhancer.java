package cz.isfgroup.sslspisumdatabox.downloader;

import org.springframework.util.MultiValueMap;

public class ZfoEnvelopeDataEnhancer implements EnvelopeDataEnhancer<ZfoEnvelopeData> {

    @Override
    public void addProperties(MultiValueMap<String, Object> body, ZfoEnvelopeData envelopeData) {
        body.add("ssl:digitalDeliveryAttachmentsCount", envelopeData.getAttachmentCount());
        body.add("ssl:digitalDeliveryDeliveryDate", envelopeData.getDeliveryTime());
        body.add("ssl:databoxRecipient", envelopeData.getRecipientId());
        body.add("ssl:databoxRecipientName", envelopeData.getRecipientName());
        body.add("ssl:databoxRecipientDataBoxType", envelopeData.getRecipientType());
        body.add("ssl:databoxSender", envelopeData.getSenderId());
        body.add("ssl:databoxSenderName", envelopeData.getSenderName());
        body.add("ssl:databoxSenderDataBoxType", envelopeData.getSenderType());
        body.add("ssl:digitalDeliverySubject", envelopeData.getSubject());
        body.add("nodeType", "ssl:databox");
        body.add("ssl:databoxRecipientUid", envelopeData.getRecipientUsername());
    }

}
