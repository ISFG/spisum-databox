package cz.isfgroup.sslspisumdatabox.downloader;

import cz.abclinuxu.datoveschranky.common.FileAttachmentStorer;
import cz.abclinuxu.datoveschranky.common.entities.Attachment;
import cz.abclinuxu.datoveschranky.common.entities.DataBoxType;
import cz.abclinuxu.datoveschranky.common.entities.MessageEnvelope;
import cz.abclinuxu.datoveschranky.common.entities.MessageState;
import cz.abclinuxu.datoveschranky.common.interfaces.AttachmentStorer;
import cz.abclinuxu.datoveschranky.common.interfaces.DataBoxDownloadService;
import cz.isfgroup.sslspisumdatabox.DataboxException;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxDownloadServiceProvider;
import cz.isfgroup.sslspisumdatabox.databox.DataBoxMessagesServiceProvider;
import cz.isfgroup.sslspisumdatabox.databox.DataboxSearchService;
import cz.isfgroup.sslspisumdatabox.processor.User;
import cz.isfgroup.sslspisumdatabox.processor.UserService;
import cz.isfgroup.sslspisumdatabox.uploader.AlfrescoNodeService;
import cz.isfgroup.sslspisumdatabox.uploader.AlfrescoService;
import cz.isfgroup.sslspisumdatabox.uploader.GetNodeChildrenModelListEntry;
import cz.isfgroup.sslspisumdatabox.uploader.GetNodeChildrenModelListEntrySingle;
import cz.isfgroup.sslspisumdatabox.uploader.ZfoChildrenMapping;
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
    private final UserService userService;
    private final DataboxSearchService databoxSearchService;
    private final AlfrescoNodeService alfrescoNodeService;

    @Async("databoxExecutor")
    public CompletableFuture<DownloadResult> download(User user) {
        long currentTimestamp = System.currentTimeMillis();
        Date from = new Date(user.getTimestamp());
        String name = user.getName();
        Date to = new Date();

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

                String zfoFileName = String.format("%s.%s", envelope.getMessageID(), "zfo");
                downloadSignedMessage(dataBoxDownloadService, envelope, zfoFileName);
                log.info("Sending file to upload processing: {}", envelope);
                GetNodeChildrenModelListEntry zfoNodeListEntry;
                try {
                    zfoNodeListEntry = alfrescoService.moveFileToUnprocessed(zfoFileName,
                        getEnvelopeDataForZfo(envelope, attachments.size(), name))
                        .get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new DataboxException(e);
                } catch (ExecutionException e) {
                    throw new DataboxException(e);
                }
                String zfoId = zfoNodeListEntry.getEntry().getId();
                String zfoPid = alfrescoNodeService.getPid(zfoId);
                List<GetNodeChildrenModelListEntry> uploadedEntries = getUploadedEntries(
                    uploadAttachments(envelope, attachments, name, zfoPid));
                sendParentChildMapping(uploadedEntries, zfoNodeListEntry);
                alfrescoNodeService.getPutComponentCount(zfoId, attachmentCount.get());
                timestamps.add(envelope.getDeliveryTime().getTimeInMillis());
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
            throw new DataboxException(e);
        } catch (ExecutionException e) {
            log.warn("Execution failed, cleaning up: {}", uploadedFiles, e);
            cleanupAlfresco(uploadedFiles);
            throw new DataboxException(e);
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

    private void downloadSignedMessage(DataBoxDownloadService dataBoxDownloadService, MessageEnvelope messageEnvelope, String zfoFileName) {
        Path zfoPath = Path.of(downloadFolder, zfoFileName);
        try (OutputStream out = Files.newOutputStream(zfoPath)) {
            dataBoxDownloadService.downloadSignedMessage(messageEnvelope, out);
        } catch (IOException e) {
            throw new DataboxException(String.format("Cannot write to file: %s", zfoPath), e);
        }
    }

    private List<CompletableFuture<GetNodeChildrenModelListEntry>> uploadAttachments(MessageEnvelope envelope,
                                                                                     List<Attachment> attachments,
                                                                                     String user,
                                                                                     String zfoPid) {
        AtomicLong attachmentCount = new AtomicLong(1);
        return attachments.stream()
            .map(attachment -> {
                AttachmentEnvelopeData envelopeData = AttachmentEnvelopeData.builder()
                    .recipientUsername(user)
                    .zfoPid(zfoPid)
                    .attachmentNumber(attachmentCount.getAndAdd(1))
                    .build();
                return alfrescoService.moveFileToUnprocessed(String.format("%s_%s", envelope.getMessageID(), attachment.getDescription()),
                    envelopeData);
            })
            .collect(Collectors.toList());
    }

    private void sendParentChildMapping(List<GetNodeChildrenModelListEntry> uploadedFiles,
                                        GetNodeChildrenModelListEntry getNodeChildrenModelListEntry) {
        uploadedFiles.stream()
            .map(GetNodeChildrenModelListEntry::getEntry)
            .map(GetNodeChildrenModelListEntrySingle::getId)
            .forEach(u -> alfrescoService.updateChild(getNodeChildrenModelListEntry.getEntry().getId(),
                ZfoChildrenMapping.builder()
                    .childId(u)
                    .build()));
    }

    private ZfoEnvelopeData getEnvelopeDataForZfo(MessageEnvelope envelope, int attachmentCount, String user) {
        DataBoxType recipientType = databoxSearchService.getDatabox(user, envelope.getRecipient().getDataBoxID()).getDataBoxType();
        DataBoxType senderType = databoxSearchService.getDatabox(user, envelope.getSender().getDataBoxID()).getDataBoxType();
        return ZfoEnvelopeData.builder()
            .deliveryTime(envelope.getDeliveryTime().toZonedDateTime().withZoneSameInstant(ZoneOffset.UTC).toString())
            .recipientId(envelope.getRecipient().getDataBoxID())
            .recipientName(envelope.getRecipient().getIdentity())
            .recipientUsername(user)
            .recipientType(recipientType != null ? recipientType.toString().toLowerCase() : null)
            .senderId(envelope.getSender().getDataBoxID())
            .senderName(envelope.getSender().getIdentity())
            .senderType(senderType != null ? senderType.toString().toLowerCase() : null)
            .subject(envelope.getAnnotation())
            .attachmentCount(attachmentCount)
            .build();
    }

}
