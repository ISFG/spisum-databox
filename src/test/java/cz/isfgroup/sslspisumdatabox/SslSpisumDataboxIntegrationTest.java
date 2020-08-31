package cz.isfgroup.sslspisumdatabox;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.abclinuxu.datoveschranky.common.entities.Attachment;
import cz.abclinuxu.datoveschranky.common.entities.DataBox;
import cz.abclinuxu.datoveschranky.common.entities.DataBoxType;
import cz.abclinuxu.datoveschranky.common.entities.DataBoxWithDetails;
import cz.abclinuxu.datoveschranky.common.entities.Message;
import cz.abclinuxu.datoveschranky.common.entities.MessageEnvelope;
import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxDownloadService;
import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxMessagesService;
import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxSearchService;
import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxUploadService;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxDownloadServiceProvider;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxMessagesServiceProvider;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxSearchServiceProvider;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxUploadServiceProvider;
import cz.isfgroup.sslspisumdatabox.downloader.DownloaderService;
import cz.isfgroup.sslspisumdatabox.processor.Status;
import cz.isfgroup.sslspisumdatabox.uploader.AlfrescoService;
import cz.isfgroup.sslspisumdatabox.uploader.GetNodeChildrenModelListEntry;
import cz.isfgroup.sslspisumdatabox.uploader.GetNodeChildrenModelListEntrySingle;
import cz.isfgroup.sslspisumdatabox.uploader.ZfoChildrenMapping;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ResourceUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SslSpisumDataboxIntegrationTest {

    public static final String TEST_FILE_TXT = "test-file.txt";
    public static final String MESSAGE_ID = "messageId";
    public static final String DOWNLOADED_ATTACHMENT_NAME = MESSAGE_ID + "_" + TEST_FILE_TXT;
    public static final String SENDER_DATABOX_ID = "senderDataboxId";
    public static final String RECIPIENT_DATABOX_ID = "recipientDataboxId";
    public static final String ATTACHMENT_UPLOADED_ID = "attachmentUploadedId";
    public static final String UNPROCESSED_ID = "unprocessedId1";
    public static final String ZFOPID = "zfopid1";
    public static final String ZFO_ID = "zfoId";
    @Value("${alfresco.repository.url}")
    private String alfrescoServerUrl;

    @Value("${alfresco.unprocessed.path}")
    private String unprocessedPath;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AlfrescoService alfrescoService;

    @Autowired
    private DownloaderService downloaderService;

    @Autowired
    private ObjectMapper objectMapper;

    @TempDir
    static Path tempDir;

    @MockBean
    private DataBoxMessagesServiceProvider dataBoxMessagesServiceProvider;

    @MockBean
    private DataBoxDownloadServiceProvider dataBoxDownloadServiceProvider;

    @MockBean
    private DataBoxSearchServiceProvider dataBoxSearchServiceProvider;

    @MockBean
    private DataBoxUploadServiceProvider dataBoxUploadServiceProvider;

    @MockBean
    private RestTemplate restTemplate;

    @Captor
    private ArgumentCaptor<HttpEntity<MultiValueMap<String, Object>>> restTemplateUploadCaptor;

    @Captor
    private ArgumentCaptor<HttpEntity<ZfoChildrenMapping>> restTemplateUpdateChild;

    @Captor
    private ArgumentCaptor<HttpEntity<GetNodeChildrenModelListEntrySingle>> restTemplateUpdateZfoCount;

    @Mock
    private DataBoxMessagesService dataBoxMessagesService;

    @Mock
    private DataBoxSearchService dataBoxSearchService;

    @Mock
    private DataBoxDownloadService dataBoxDownloadService;

    @Mock
    private DataBoxUploadService dataBoxUploadService;

    @Test
    void testMessageDownload() throws Exception {
        prepareMocks();
        mockGetUnprocessedFolderIdCall();
        mockGetTimestampsCall();
        MessageEnvelope envelope = mockGetListOfMessagesCall();
        mockDownloadMessageCall(envelope);
        mockUploadFileCall();
        mockUpdateChildrenMock();
        mockSearchDataboxesCall();
        mockGetZfoGetPidCall();

        String result = startProcess();
        checkNumberOfProcessedFiles(result);

        verify(restTemplate, times(2)).postForEntity(
            eq(getUploadUrl()),
            restTemplateUploadCaptor.capture(),
            eq(GetNodeChildrenModelListEntry.class));
        List<HttpEntity<MultiValueMap<String, Object>>> uploadedFiles = restTemplateUploadCaptor.getAllValues();
        attachmentAsserts(uploadedFiles);
        zfoAsserts(uploadedFiles);

        verify(restTemplate, times(1)).postForEntity(
            eq(getSecondaryChildrenUrl()),
            restTemplateUpdateChild.capture(),
            eq(String.class));
        updateChildrenAssert();

        verify(restTemplate, times(1)).exchange(
            eq(getNodeUrl()),
            eq(HttpMethod.PUT),
            restTemplateUpdateZfoCount.capture(),
            eq(GetNodeChildrenModelListEntry.class));
        assertEquals("1", restTemplateUpdateZfoCount.getValue().getBody().getProperties().get("ssl:componentCounter"));
    }

    private void updateChildrenAssert() {
        assertEquals("ssl:digitalDeliveryAttachments", restTemplateUpdateChild.getValue().getBody().getAssocType());
        assertEquals(ATTACHMENT_UPLOADED_ID, restTemplateUpdateChild.getValue().getBody().getChildId());
    }


    private void zfoAsserts(List<HttpEntity<MultiValueMap<String, Object>>> uploadedFiles) {
        assertEquals("ovm_exekut", getUploadedPropertyString(uploadedFiles, 0, "ssl:databoxSenderDataBoxType"));
        assertEquals("ovm_req", getUploadedPropertyString(uploadedFiles, 0, "ssl:databoxRecipientDataBoxType"));
        assertTrue(((FileSystemResource) getUploadedProperty(uploadedFiles, 0, "filedata")).getPath().endsWith(MESSAGE_ID + ".zfo"));
        assertEquals("bla", getUploadedPropertyString(uploadedFiles, 0, "ssl:databoxRecipientUid"));
        assertEquals("ssl:databox", getUploadedPropertyString(uploadedFiles, 0, "nodeType"));
        assertEquals(MESSAGE_ID + ".zfo", getUploadedPropertyString(uploadedFiles, 0, "ssl:fileName"));
        assertEquals(SENDER_DATABOX_ID, getUploadedPropertyString(uploadedFiles, 0, "ssl:databoxSender"));
        assertEquals(RECIPIENT_DATABOX_ID, getUploadedPropertyString(uploadedFiles, 0, "ssl:databoxRecipient"));
        assertEquals(1, (int) getUploadedProperty(uploadedFiles, 0, "ssl:digitalDeliveryAttachmentsCount"));
    }

    private void attachmentAsserts(List<HttpEntity<MultiValueMap<String, Object>>> uploadedFiles) {
        assertTrue(((FileSystemResource) getUploadedProperty(uploadedFiles, 1, "filedata")).getPath().endsWith(DOWNLOADED_ATTACHMENT_NAME));
        assertEquals("bla", getUploadedPropertyString(uploadedFiles, 1, "ssl:databoxRecipientUid"));
        assertEquals("ssl:component", getUploadedPropertyString(uploadedFiles, 1, "nodeType"));
        assertEquals(DOWNLOADED_ATTACHMENT_NAME, getUploadedPropertyString(uploadedFiles, 1, "ssl:fileName"));
        assertEquals(ZFOPID + "/1", getUploadedPropertyString(uploadedFiles, 1, "ssl:pid"));
    }

    private String getUploadedPropertyString(List<HttpEntity<MultiValueMap<String, Object>>> uploadedFiles, int order, String field) {
        return (String) getUploadedProperty(uploadedFiles, order, field);
    }

    private Object getUploadedProperty(List<HttpEntity<MultiValueMap<String, Object>>> uploadedFiles, int order, String field) {
        return uploadedFiles.get(order).getBody().get(field).get(0);
    }

    private void mockSearchDataboxesCall() {
        DataBoxWithDetails databoxWithDetailsSender = new DataBoxWithDetails();
        databoxWithDetailsSender.setDataBoxType(DataBoxType.OVM_EXEKUT);
        DataBoxWithDetails databoxWithDetailsRecipient = new DataBoxWithDetails();
        databoxWithDetailsRecipient.setDataBoxType(DataBoxType.OVM_REQ);
        when(dataBoxSearchService.findDataBoxByID(eq(SENDER_DATABOX_ID))).thenReturn(databoxWithDetailsSender);
        when(dataBoxSearchService.findDataBoxByID(eq(RECIPIENT_DATABOX_ID))).thenReturn(databoxWithDetailsRecipient);
    }

    private void mockUpdateChildrenMock() {
        String secondaryChildrenUrl = getSecondaryChildrenUrl();

        when(restTemplate.postForEntity(eq(secondaryChildrenUrl), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>("a response", HttpStatus.OK));
    }

    private String getSecondaryChildrenUrl() {
        return String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s/secondary-children",
            alfrescoServerUrl,
            ZFO_ID);
    }

    private void mockUploadFileCall() {
        String uploadUrl = getUploadUrl();

        GetNodeChildrenModelListEntry uploadEntry = GetNodeChildrenModelListEntry.builder()
            .entry(GetNodeChildrenModelListEntrySingle.builder()
                .id(ATTACHMENT_UPLOADED_ID)
                .build())
            .build();
        GetNodeChildrenModelListEntry uploadZfo = GetNodeChildrenModelListEntry.builder()
            .entry(GetNodeChildrenModelListEntrySingle.builder()
                .id(ZFO_ID)
                .build())
            .build();
        when(restTemplate.postForEntity(eq(uploadUrl), any(), eq(GetNodeChildrenModelListEntry.class)))
            .thenReturn(new ResponseEntity<>(uploadZfo, HttpStatus.OK)).thenReturn(new ResponseEntity<>(uploadEntry, HttpStatus.OK));
    }

    private void mockGetZfoGetPidCall() {
        String getNodeUrl = getNodeUrl();

        GetNodeChildrenModelListEntry getZfo = GetNodeChildrenModelListEntry.builder()
            .entry(GetNodeChildrenModelListEntrySingle.builder()
                .properties(Map.of("ssl:pid", ZFOPID))
                .build())
            .build();
        when(restTemplate.exchange(eq(getNodeUrl), eq(HttpMethod.GET), any(), eq(GetNodeChildrenModelListEntry.class)))
            .thenReturn(new ResponseEntity<>(getZfo, HttpStatus.OK));
    }

    private String getNodeUrl() {
        return String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s",
            alfrescoServerUrl,
            ZFO_ID);
    }

    private String getUploadUrl() {
        return String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s/children",
            alfrescoServerUrl,
            UNPROCESSED_ID);
    }

    private void mockDownloadMessageCall(MessageEnvelope envelope) {
        Message downloadedMessage = new Message();
        Attachment attachment = new Attachment();
        attachment.setDescription(TEST_FILE_TXT);
        List<Attachment> attachments = Collections.singletonList(attachment);
        downloadedMessage.setAttachments(attachments);
        when(dataBoxDownloadService.downloadMessage(eq(envelope), any())).thenReturn(downloadedMessage);
    }

    private MessageEnvelope mockGetListOfMessagesCall() {
        MessageEnvelope envelope = new MessageEnvelope();
        envelope.setMessageID(MESSAGE_ID);
        DataBox senderDatabox = new DataBox("senderDataboxId");
        envelope.setSender(senderDatabox);
        DataBox recipientDatabox = new DataBox("recipientDataboxId");
        envelope.setRecipient(recipientDatabox);
        envelope.setDeliveryTime(GregorianCalendar.from(ZonedDateTime.now().minus(1, ChronoUnit.HOURS)));
        when(dataBoxMessagesService.getListOfReceivedMessages(any(), any(), any(), anyInt(), anyInt())).thenReturn(
            Collections.singletonList(envelope));
        return envelope;
    }

    private void mockGetTimestampsCall() {
        GetNodeChildrenModelListEntry timestampsEntry = GetNodeChildrenModelListEntry.builder()
            .entry(GetNodeChildrenModelListEntrySingle.builder()
                .id(UNPROCESSED_ID)
                .properties(new HashMap<>())
                .build()
            )
            .build();

        String timestampsUrl = String.format("%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/%s",
            alfrescoServerUrl,
            UNPROCESSED_ID);

        when(restTemplate.exchange(eq(timestampsUrl), eq(HttpMethod.GET), any(), eq(GetNodeChildrenModelListEntry.class)))
            .thenReturn(new ResponseEntity<>(timestampsEntry, HttpStatus.OK));
    }

    private String mockGetUnprocessedFolderIdCall() {
        String unprocessedIdUrl = String.format(
            "%s/alfresco/api/-default-/public/alfresco/versions/1/nodes/-root-/?skipCount=0&maxItems=1&orderBy=createdAt DESC&relativePath=%s",
            alfrescoServerUrl,
            unprocessedPath);

        GetNodeChildrenModelListEntry listEntry = GetNodeChildrenModelListEntry.builder()
            .entry(GetNodeChildrenModelListEntrySingle.builder()
                .id(UNPROCESSED_ID)
                .build()
            )
            .build();

        when(restTemplate.exchange(eq(unprocessedIdUrl), eq(HttpMethod.GET), any(), eq(GetNodeChildrenModelListEntry.class)))
            .thenReturn(new ResponseEntity<>(listEntry, HttpStatus.OK));
        return UNPROCESSED_ID;
    }

    private void prepareMocks() throws IOException {
        ReflectionTestUtils.setField(alfrescoService, "downloadFolder", tempDir.toAbsolutePath().toString());
        ReflectionTestUtils.setField(downloaderService, "downloadFolder", tempDir.toAbsolutePath().toString());

        Files.copy(ResourceUtils.getFile("classpath:" + TEST_FILE_TXT).toPath(),
            Paths.get(tempDir.toAbsolutePath().toString(), DOWNLOADED_ATTACHMENT_NAME));

        when(dataBoxMessagesServiceProvider.getDataBoxMessagesService(any())).thenReturn(dataBoxMessagesService);
        when(dataBoxDownloadServiceProvider.getDataBoxDownloadService(any())).thenReturn(dataBoxDownloadService);
        when(dataBoxSearchServiceProvider.getDataBoxSearchService(any())).thenReturn(dataBoxSearchService);
        when(dataBoxUploadServiceProvider.getDataBoxUploadService(any())).thenReturn(dataBoxUploadService);
    }

    private String startProcess() throws Exception {
        String result = mvc.perform(post("/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        assertEquals("0", result);
        return result;
    }

    private void checkNumberOfProcessedFiles(String fileCount) throws Exception {
        String statusResponse = mvc.perform(get("/status")
            .param("id", fileCount)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
            .andDo(print())
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

        Status status = objectMapper.readValue(statusResponse, Status.class);

        assertEquals(1, status.getNewMessageCount());
        assertFalse(status.isRunning());
    }

}
