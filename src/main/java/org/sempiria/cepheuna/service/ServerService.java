package org.sempiria.cepheuna.service;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.AudioProperties;
import org.sempiria.cepheuna.dto.ChatRequest;
import org.sempiria.cepheuna.dto.UserAudioRequest;
import org.sempiria.cepheuna.enums.ConversationState;
import org.sempiria.cepheuna.memory.MemoryOrchestratorLLMServiceImpl;
import org.sempiria.cepheuna.memory.dto.ConversationEntity;
import org.sempiria.cepheuna.repository.storage.ConversationStore;
import org.sempiria.cepheuna.utils.AudioUtil;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * End-to-end server pipeline:
 * STT -> LLM(stream) -> tokenizer -> TTS(stream) -> websocket.
 *
 * <p>This revision keeps sentence-level TTS input segmentation, but runs sentence TTS jobs in
 * parallel and streams chunks to the browser as soon as they are ready. Playback ordering is
 * preserved client-side using (seq, chunkIndex) and the explicit sentence-complete signal.
 *
 * @since 1.0.0
 * @version 1.2.0
 * @author Sempiria
 */
@Service
@Slf4j
public class ServerService {
    private static final int TTS_PARALLELISM = 3;

    private final ConversationStore conversationStore;
    private final LLMService llmService;
    private final TtsService ttsService;
    private final SherpaOnnxSttService sherpaSttService;
    private final AudioProperties audioProperties;
    private final StreamingTokenizerServiceStore streamingTokenizerServiceStore;

    private final Map<String, TtsPipeline> ttsPipelines = new ConcurrentHashMap<>();

    public ServerService(
            ConversationStore conversationStore,
            MemoryOrchestratorLLMServiceImpl llmService,
            TtsService ttsService,
            SherpaOnnxSttService sherpaSttService,
            AudioProperties audioProperties,
            StreamingTokenizerServiceStore streamingTokenizerServiceStore
    ) {
        this.conversationStore = conversationStore;
        this.llmService = llmService;
        this.ttsService = ttsService;
        this.sherpaSttService = sherpaSttService;
        this.audioProperties = audioProperties;
        this.streamingTokenizerServiceStore = streamingTokenizerServiceStore;
    }

    public void onUserText(@NonNull String cid, @NonNull String text, @NonNull OutstreamService outstreamService) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            outstreamService.onError(cid, "Empty user conversation.");
            return;
        }

        ConversationEntity conversation = conversationStore.getConversationMemoryOrStorage(cid);
        startAssistantTurn(conversation, normalized, outstreamService);
    }

    public void onUserVoice(@NonNull String cid, @NonNull ByteBuffer bytes, @NonNull OutstreamService outstreamService) {
        ConversationEntity conversation = conversationStore.getConversationMemoryOrStorage(cid);

        byte[] raw = new byte[bytes.remaining()];
        bytes.get(raw);

        float[] samples = AudioUtil.toFloatArray(raw);
        if (samples.length == 0) {
            return;
        }

        float rms = AudioUtil.rms(samples);
        boolean speaking = rms >= audioProperties.getVadRmsThreshold();

        if (conversation.isAssistantActive() && speaking) {
            stopConversation(cid, outstreamService);
        }

        sherpaSttService.acceptWaveform(cid, samples, audioProperties.getSampleRate());
        String partial = sherpaSttService.getText(cid);

        if (speaking) {
            if (conversation.state != ConversationState.LISTENING) {
                conversation.state = ConversationState.LISTENING;
                conversation.speechFrames = 0;
                conversation.silenceFrames = 0;
            }
            conversation.speechFrames++;
            conversation.silenceFrames = 0;

            if (!partial.isBlank() && !partial.equals(conversation.lastPartial)) {
                conversation.lastPartial = partial;
                outstreamService.onUserPartialText(new UserAudioRequest(partial, cid, null, conversation.segmentIndex));
            }
            return;
        }

        if (conversation.state == ConversationState.LISTENING) {
            conversation.silenceFrames++;

            if (!partial.isBlank() && !partial.equals(conversation.lastPartial)) {
                conversation.lastPartial = partial;
                outstreamService.onUserPartialText(new UserAudioRequest(partial, cid, null, conversation.segmentIndex));
            }

            boolean endpoint = sherpaSttService.isEndpoint(cid)
                    || conversation.silenceFrames >= audioProperties.getSilenceTriggerFrame();

            if (endpoint) {
                finalizeUserSpeech(conversation, outstreamService);
            }
        }
    }

    public void stopConversation(@NonNull String cid, @NonNull OutstreamService outstreamService) {
        ConversationEntity conversation = conversationStore.getConversationMemoryOrStorage(cid);

        llmService.cancelCurrent(cid);
        conversation.cancelAssistant();
        conversation.state = ConversationState.IDLE;
        conversation.lastPartial = "";
        conversation.segmentIndex = 0;
        conversation.silenceFrames = 0;
        conversation.speechFrames = 0;
        conversation.currentUtteranceId = null;

        streamingTokenizerServiceStore.reset(cid);
        completeTtsPipeline(cid);
        outstreamService.stop();
        outstreamService.onStopped(cid);

        sherpaSttService.reset(cid);
    }

    private void finalizeUserSpeech(@NonNull ConversationEntity conversation, @NonNull OutstreamService outstreamService) {
        String cid = conversation.getCid();
        String finalText = sherpaSttService.getText(cid).trim();

        sherpaSttService.reset(cid);

        long speechFrames = conversation.speechFrames;
        conversation.silenceFrames = 0;
        conversation.speechFrames = 0;
        conversation.state = ConversationState.IDLE;

        if (speechFrames < audioProperties.getSpeechTriggerFrame()) {
            conversation.lastPartial = "";
            return;
        }

        if (finalText.isBlank()) {
            conversation.lastPartial = "";
            return;
        }

        conversation.lastPartial = finalText;
        outstreamService.onUserFinalText(new UserAudioRequest(finalText, cid, null, 0));
        startAssistantTurn(conversation, finalText, outstreamService);
    }

    private void startAssistantTurn(
            @NonNull ConversationEntity conversation,
            @NonNull String text,
            @NonNull OutstreamService outstreamService
    ) {
        String cid = conversation.getCid();

        llmService.cancelCurrent(cid);
        conversation.cancelAssistant();
        streamingTokenizerServiceStore.reset(cid);
        completeTtsPipeline(cid);

        conversation.currentUtteranceId = cid + "-" + conversation.turnCounter.incrementAndGet() + "-" + UUID.randomUUID();
        conversation.segmentIndex = 0;
        conversation.lastPartial = "";
        conversation.setAssistantActive(true);
        conversation.state = ConversationState.THINKING;

        StreamingTokenizerService tokenizer = streamingTokenizerServiceStore.getInstance(cid);
        tokenizer.reset();

        TtsPipeline pipeline = createTtsPipeline(cid, outstreamService);

        conversation.currentAssistantSubscription = llmService.stream(new ChatRequest(conversation,text))
                .doOnNext((event) -> handleAssistantEvent(conversation, tokenizer, pipeline, outstreamService, event))
                .doOnError((ex) -> {
                    tokenizer.flush((segment) -> enqueueTtsSegment(conversation, pipeline, outstreamService, segment));
                    completeTtsPipeline(cid);
                    conversation.currentAssistantSubscription = null;
                    conversation.setAssistantActive(false);
                    conversation.state = ConversationState.IDLE;
                    outstreamService.onError(cid, ex.getMessage() == null ? "LLM stream failed." : ex.getMessage());
                })
                .doOnComplete(() -> {
                    tokenizer.flush((segment) -> enqueueTtsSegment(conversation, pipeline, outstreamService, segment));
                    completeTtsPipeline(cid);
                    conversation.currentAssistantSubscription = null;
                    conversation.setAssistantActive(false);
                    conversation.state = ConversationState.IDLE;
                })
                .onErrorResume((ex) -> Flux.empty())
                .subscribe(
                        null,
                        (ex) -> log.debug("Assistant stream already handled error. cid={}", cid, ex)
                );
    }

    private void handleAssistantEvent(
            @NonNull ConversationEntity conversation,
            @NonNull StreamingTokenizerService tokenizer,
            @NonNull TtsPipeline pipeline,
            @NonNull OutstreamService outstreamService,
            @NonNull ServerSentEvent<String> event
    ) {
        outstreamService.onAssistantEvent(event);

        String eventName = event.event();
        String data = event.data();

        if ("token".equals(eventName) && data != null && !data.isEmpty()) {
            conversation.state = ConversationState.REPLYING;
            boolean aggressive = conversation.segmentIndex == 0;
            tokenizer.feed(data, aggressive, (segment) -> enqueueTtsSegment(conversation, pipeline, outstreamService, segment));
        }
    }

    private void enqueueTtsSegment(
            @NonNull ConversationEntity conversation,
            @NonNull TtsPipeline pipeline,
            @NonNull OutstreamService outstreamService,
            @Nullable String sentence
    ) {
        String normalized = sentence == null ? "" : sentence.trim();
        if (normalized.isEmpty()) {
            return;
        }

        String utteranceId = conversation.currentUtteranceId == null ? "" : conversation.currentUtteranceId;
        long seq = conversation.segmentIndex++;
        outstreamService.onAssistantTtsQueued(utteranceId, seq, normalized);

        UserAudioRequest request = new UserAudioRequest(normalized, conversation.getCid(), utteranceId, seq);
        pipeline.emit(request);
    }

    private @NonNull TtsPipeline createTtsPipeline(@NonNull String cid, @NonNull OutstreamService outstreamService) {
        TtsPipeline existing = ttsPipelines.remove(cid);
        if (existing != null) {
            existing.dispose();
        }

        Sinks.Many<UserAudioRequest> sink = Sinks.many().unicast().onBackpressureBuffer();
        Disposable disposable = sink.asFlux()
                .flatMap((req) -> {
                            AtomicLong chunkCounter = new AtomicLong(0L);
                            return ttsService.ttsStream(req.text())
                                    .doOnNext((chunk) -> outstreamService.onAssistantAudioChunk(
                                            req.cid(),
                                            req.utteranceId() == null ? "" : req.utteranceId(),
                                            req.segmentIndex(),
                                            chunkCounter.getAndIncrement(),
                                            chunk,
                                            ttsService.outputFormat()
                                    ))
                                    .doOnComplete(() -> outstreamService.onAssistantAudioComplete(
                                            req.cid(),
                                            req.utteranceId() == null ? "" : req.utteranceId(),
                                            req.segmentIndex()
                                    ))
                                    .then(Mono.empty());
                        }, TTS_PARALLELISM)
                .doOnError((ex) -> outstreamService.onError(cid, ex.getMessage() == null ? "TTS failed." : ex.getMessage()))
                .doOnComplete(() -> outstreamService.onAssistantDone(cid))
                .onErrorResume((ex) -> Flux.empty())
                .subscribe(
                        null,
                        (ex) -> log.debug("TTS pipeline already handled error. cid={}", cid, ex)
                );

        TtsPipeline pipeline = new TtsPipeline(sink, disposable);
        ttsPipelines.put(cid, pipeline);
        return pipeline;
    }

    private void completeTtsPipeline(@NonNull String cid) {
        TtsPipeline pipeline = ttsPipelines.remove(cid);
        if (pipeline != null) {
            pipeline.complete();
        }
    }

    private record TtsPipeline(Sinks.Many<UserAudioRequest> sink, Disposable disposable) {
        void emit(UserAudioRequest request) {
            sink.tryEmitNext(request);
        }

        void complete() {
            sink.tryEmitComplete();
        }

        void dispose() {
            sink.tryEmitComplete();
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
        }
    }
}
