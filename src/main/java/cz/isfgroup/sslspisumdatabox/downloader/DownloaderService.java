package cz.isfgroup.sslspisumdatabox.downloader;

import cz.abclinuxu.datoveschranky.common.FileAttachmentStorer;
import cz.abclinuxu.datoveschranky.common.entities.Attachment;
import cz.abclinuxu.datoveschranky.common.entities.MessageEnvelope;
import cz.abclinuxu.datoveschranky.common.entities.MessageState;
import cz.abclinuxu.datoveschranky.common.interfaces.AttachmentStorer;
import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxDownloadService;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxDownloadServiceProvider;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxMessagesServiceProvider;
import cz.isfgroup.sslspisumdatabox.processor.User;
import cz.isfgroup.sslspisumdatabox.processor.UserService;
import cz.isfgroup.sslspisumdatabox.uploader.AlfrescoNodeIdService;
import cz.isfgroup.sslspisumdatabox.uploader.AlfrescoService;
import cz.isfgroup.sslspisumdatabox.uploader.GetNodeChildrenModelListEntry;
import cz.isfgroup.sslspisumdatabox.uploader.GetNodeChildrenModelListEntrySingle;
import cz.isfgroup.sslspisumdatabox.uploader.ZfoChildrenMapping;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Slf4j
@Service
public class DownloaderService {

    @Value("${download.folder:/tmp}")
    private String downloadFolder;

    private final DataBoxMessagesServiceProvider dataBoxMessagesServiceProvider;
    private final DataBoxDownloadServiceProvider dataBoxDownloadServiceProvider;
    private final AlfrescoService alfrescoService;
    private final AlfrescoNodeIdService alfrescoNodeIdService;
    private final UserService userService;

    @Async("databoxExecutor")
    public CompletableFuture<DownloadResult> download(User user) {
        Date from = new Date(user.getTimestamp());
        // 0,1 second overlap just to be sure because of Internet :D
        String name = user.getName();
        Date to = new Date();
        long currentTimestamp = System.currentTimeMillis() - 100;

        // no paging
        int offset = 1;
        int limit = Integer.MAX_VALUE;
        // filter = null -> don't filter
        EnumSet<MessageState> filter = null;
        log.info(String.format("fetching envelopes received from %s to %s", from, to));
        List<MessageEnvelope> envelopes = dataBoxMessagesServiceProvider
            .getDataBoxMessagesService(name)
            .getListOfReceivedMessages(from, to, filter, offset, limit)
            .stream()
            .sorted(Comparator.comparing(MessageEnvelope::getDeliveryTime))
            .collect(Collectors.toList());

        if (envelopes.isEmpty()) {
            userService.setUserTimestamp(name, currentTimestamp, null);
            return CompletableFuture.completedFuture(null);
        }

        File dirForAttachments = new File(downloadFolder);
        dirForAttachments.mkdirs();

        DownloadResult result = processEnvelopes(name, envelopes, currentTimestamp);

        return CompletableFuture.completedFuture(result);
    }

    private DownloadResult processEnvelopes(String name, List<MessageEnvelope> envelopes, long currentTimestamp) {
        File dirForAttachments = new File(downloadFolder);
        dirForAttachments.mkdirs();
        AttachmentStorer storeForMessageAttachments = new FileAttachmentStorer(dirForAttachments);
        DataBoxDownloadService dataBoxDownloadService = dataBoxDownloadServiceProvider.getDataBoxDownloadService(name);
        AtomicLong attachmentCount = new AtomicLong(0);
        List<Long> timestamps = new CopyOnWriteArrayList<>();
        try {
            envelopes.forEach(envelope -> {
                List<Attachment> attachments = dataBoxDownloadService.downloadMessage(envelope,
                    storeForMessageAttachments).getAttachments();
                attachmentCount.addAndGet(attachments.size());
                String folderId = alfrescoNodeIdService.getUnprocessedNodeId();
                List<GetNodeChildrenModelListEntry> uploadedEntries = getUploadedEntries(
                    uploadAttachments(envelope, folderId, attachments, name));

                String zfoFileName = String.format("%s.%s", envelope.getMessageID(), "zfo");
                Path zfoPath = Path.of(downloadFolder, zfoFileName);
                MetaData metaData = getZfoMetaData(dataBoxDownloadService, envelope, attachments, zfoFileName, zfoPath, name);
                log.info("Sending file to upload processing: {}", envelope);
                CompletableFuture<GetNodeChildrenModelListEntry> zfoUploadFuture = alfrescoService.moveFile(metaData.getLocalFileName(),
                    folderId, metaData.getEnvelopeData());
                try {
                    zfoUploadFuture
                        .thenAccept(nodeChildrenModelListEntry -> sendParentChildMapping(uploadedEntries, nodeChildrenModelListEntry))
                        .get();
                    timestamps.add(envelope.getDeliveryTime().getTimeInMillis());
                } catch (InterruptedException e) {
                    log.warn("Interrupted, cleaning up: {}", uploadedEntries, e);
                    deleteEntries(uploadedEntries.stream());
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    log.warn("Execution exception when processing envelope, cleaning up: {}", uploadedEntries, e);
                    deleteEntries(uploadedEntries.stream());
                    throw new RuntimeException(e);
                }
            });
            userService.setUserTimestamp(name, currentTimestamp, timestamps.get(timestamps.size() - 1));
        } catch (Exception e) {
            log.warn("Caught exception when processing envelopes. Resetting timestamp to the last successful", e);
            if (!timestamps.isEmpty()) {
                userService.setUserTimestamp(name, timestamps.get(timestamps.size() - 1), timestamps.get(timestamps.size() - 1) + 1);
            }
            return DownloadResult.builder().error(Collections.singletonList(e)).build();
        }
        return DownloadResult.builder().attachmentCount(attachmentCount.get()).build();
    }

    private List<GetNodeChildrenModelListEntry> getUploadedEntries(List<CompletableFuture<GetNodeChildrenModelListEntry>> uploadedFiles) {
        CompletableFuture<Void> allFutures = CompletableFuture
            .allOf(uploadedFiles.toArray(new CompletableFuture[uploadedFiles.size()]));
        CompletableFuture<List<GetNodeChildrenModelListEntry>> allCompletableFuture = allFutures.thenApply(
            future -> uploadedFiles.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
        try {
            return allCompletableFuture.thenApply(ArrayList::new).get();
        } catch (InterruptedException e) {
            log.warn("Interrupted, cleaning up: {}", uploadedFiles, e);
            cleanupAlfresco(uploadedFiles);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            log.warn("Execution failed, cleaning up: {}", uploadedFiles, e);
            cleanupAlfresco(uploadedFiles);
            throw new RuntimeException(e);
        }
    }

    private void cleanupAlfresco(List<CompletableFuture<GetNodeChildrenModelListEntry>> uploadedFiles) {
        deleteEntries(uploadedFiles.parallelStream()
            .filter(Predicate.not(CompletableFuture::isCompletedExceptionally))
            .map(CompletableFuture::join));
    }

    private void deleteEntries(Stream<GetNodeChildrenModelListEntry> entries) {
        entries.map(GetNodeChildrenModelListEntry::getEntry)
            .map(GetNodeChildrenModelListEntrySingle::getId)
            .forEach(alfrescoService::deleteNode);
    }

    private MetaData getZfoMetaData(DataBoxDownloadService dataBoxDownloadService,
                                    MessageEnvelope t, List<Attachment> attachments, String zfoFileName, Path zfoPath, String user) {
        MetaData metaData;
        try (OutputStream out = Files.newOutputStream(zfoPath)) {
            dataBoxDownloadService.downloadSignedMessage(t, out);
            EnvelopeData envelopeData = getEnvelopeDataForZfo(t, attachments.size(), user);
            metaData = getMetaData(zfoFileName, envelopeData);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Cannot write to file: %s", zfoPath), e);
        }
        return metaData;
    }

    private List<CompletableFuture<GetNodeChildrenModelListEntry>> uploadAttachments(MessageEnvelope t, String folderId,
                                                                                     List<Attachment> attachments,
                                                                                     String user) {
        return attachments.stream()
            .map(u -> {
                EnvelopeData envelopeData = EnvelopeData.builder().nodeType("cm:content").recipientUsername(user).build();
                return alfrescoService.moveFile(String.format("%s_%s", t.getMessageID(), u.getDescription()), folderId, envelopeData);
            })
            .collect(Collectors.toList());
    }

    private void sendParentChildMapping(List<GetNodeChildrenModelListEntry> uploadedFiles,
                                        GetNodeChildrenModelListEntry getNodeChildrenModelListEntry) {
        uploadedFiles.parallelStream()
            .map(GetNodeChildrenModelListEntry::getEntry)
            .map(GetNodeChildrenModelListEntrySingle::getId)
            .forEach(u -> alfrescoService.updateChild(getNodeChildrenModelListEntry.getEntry().getId(),
                ZfoChildrenMapping.builder()
                    .childId(u)
                    .build()));
    }

    private EnvelopeData getEnvelopeDataForZfo(MessageEnvelope envelope, int attachmentCount, String user) {
        return EnvelopeData.builder()
            .nodeType("ssl:databox")
            .deliveryTime(envelope.getDeliveryTime().toZonedDateTime().withZoneSameInstant(ZoneOffset.UTC).toString())
            .recipientId(envelope.getRecipient().getDataBoxID())
            .recipientName(envelope.getRecipient().getIdentity())
            .recipientUsername(user)
            .senderId(envelope.getSender().getDataBoxID())
            .senderName(envelope.getSender().getIdentity())
            .subject(envelope.getAnnotation())
            .attachmentCount(attachmentCount)
            .build();
    }

    private MetaData getMetaData(String zfoFileName, EnvelopeData envelopeData) {
        return MetaData.builder()
            .localFileName(zfoFileName)
            .envelopeData(envelopeData)
            .build();
    }

    @Builder
    @Data
    private static class MetaData {
        private String localFileName;
        private EnvelopeData envelopeData;
    }

}
