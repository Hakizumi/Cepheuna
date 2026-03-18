package org.sempiria.cepheuna.repository.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.sempiria.cepheuna.config.ModelProperties;
import org.sempiria.cepheuna.memory.SummaryState;
import org.sempiria.cepheuna.memory.dto.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conversation store with in-memory cache plus file persistence.
 *
 * <p>The name is kept for compatibility with the existing wiring, but this implementation is now a
 * single-machine production store. Memory remains as a hot cache, while layered-memory files are
 * persisted under the configured session directory.
 */
@Component
public class RAMConversationStoreImpl implements ConversationStore {
    private static final String META_FILE = "meta.json";
    private static final String FACTS_FILE = "facts.json";
    private static final String SUMMARY_FILE = "summary.json";
    private static final String RECENT_FILE = "recent.jsonl";
    private static final String ARCHIVE_DIR = "archive";
    private static final String VECTOR_DIR = "vector";
    private static final String VECTOR_FILE = "index.json";

    private final @NonNull Map<String, ConversationEntity> memories;
    private final ObjectMapper objectMapper;
    private final @NonNull Path baseDir;

    public RAMConversationStoreImpl(@NonNull ObjectMapper objectMapper, @NonNull ModelProperties modelProperties) {
        this.memories = new ConcurrentHashMap<>();
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.baseDir = Paths.get(modelProperties.getMemory().getBaseDir()).toAbsolutePath().normalize();
        mkdirs(baseDir);
    }

    @Override
    public @Nullable ConversationEntity getConversationMemory(@NonNull String cid) {
        return memories.get(cid);
    }

    @Override
    public @NonNull ConversationEntity getConversationMemoryOrDefault(@NonNull String cid) {
        ConversationEntity entity = memories.get(cid);
        return entity != null ? entity : new ConversationEntity(cid);
    }

    @Override
    public @NonNull ConversationEntity getConversationMemoryOrStorage(@NonNull String cid) {
        return memories.computeIfAbsent(cid, this::loadConversationEntity);
    }

    @Override
    public ConversationEntity removeConversationMemory(@NonNull String cid) {
        ConversationEntity removed = memories.remove(cid);
        deleteRecursively(sessionDir(cid));
        return removed;
    }

    @Override
    public void addConversationMemory(@NonNull String cid, @NonNull ConversationEntity conversationEntity) {
        memories.put(cid, conversationEntity);
        persistAll(cid, conversationEntity);
    }

    @Override
    public void clearConversationMemory(@Nullable String cid) {
        memories.remove(cid);
        if (cid != null && cid.endsWith("::memory")) {
            return;
        }
        deleteRecursively(sessionDir(cid));
    }

    @Override
    public void clearAll() {
        memories.clear();
        deleteRecursively(baseDir);
        mkdirs(baseDir);
    }

    @Override
    public @Nullable ConversationEntity pushMessage(@NonNull String cid, Message message, boolean putIfAbsent) {
        ConversationEntity entity = putIfAbsent ? getConversationMemoryOrStorage(cid) : getConversationMemory(cid);
        if (entity == null) {
            return null;
        }
        entity.pushMessage(message);
        persistRecent(cid, entity.getMessages());
        return entity;
    }

    @Override
    public @Nullable ConversationEntity pushMessage(@NonNull String cid, Message message) {
        return pushMessage(cid, message, true);
    }

    @Override
    public @NonNull SessionMeta loadMetaOrCreate(@NonNull String cid) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        entity.getMeta().setSessionId(cid);
        persistMeta(cid, entity.getMeta());
        return entity.getMeta();
    }

    @Override
    public void saveMeta(@NonNull String cid, @NonNull SessionMeta meta) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        entity.getMeta().setSessionId(cid);
        entity.getMeta().setLastChunkSeq(meta.getLastChunkSeq());
        entity.getMeta().setSummaryVersion(meta.getSummaryVersion());
        entity.getMeta().setVectorVersion(meta.getVectorVersion());
        entity.getMeta().setLastUpdatedAt(meta.getLastUpdatedAt());
        persistMeta(cid, entity.getMeta());
    }

    @Override
    public @NonNull Facts loadFactsOrCreate(@NonNull String cid) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        persistFacts(cid, entity.getFacts());
        return entity.getFacts();
    }

    @Override
    public void saveFacts(@NonNull String cid, @NonNull Facts facts) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        entity.getFacts().setFacts(new LinkedHashMap<>(facts.getFacts()));
        persistFacts(cid, entity.getFacts());
    }

    @Override
    public @NonNull SummaryState loadSummaryOrCreate(@NonNull String cid) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        persistSummary(cid, entity.getSummary());
        return entity.getSummary();
    }

    @Override
    public void saveSummary(@NonNull String cid, @NonNull SummaryState summary) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        entity.getSummary().setEntries(new LinkedHashMap<>(summary.getEntries()));
        entity.getSummary().setUpdatedAt(summary.getUpdatedAt());
        persistSummary(cid, entity.getSummary());
    }

    @Override
    public @NonNull List<Message> loadRecent(@NonNull String cid) {
        return new ArrayList<>(getConversationMemoryOrStorage(cid).getMessages());
    }

    @Override
    public void rewriteRecent(@NonNull String cid, @Nullable List<Message> messages) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        entity.getMessages().clear();
        if (messages != null) {
            entity.getMessages().addAll(messages);
        }
        persistRecent(cid, entity.getMessages());
    }

    @Override
    public @NonNull ChunkMeta writeArchiveChunk(@NonNull String cid, String chunkId, @Nullable List<Message> messages) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        List<Message> copied = new ArrayList<>(messages == null ? List.of() : messages);
        entity.getArchiveChunks().put(chunkId, copied);

        ChunkMeta meta = new ChunkMeta();
        meta.setChunkId(chunkId);
        meta.setFileName(chunkId + ".jsonl");
        meta.setMessageCount(copied.size());
        meta.setStartTs(extractStartTs());
        meta.setEndTs(System.currentTimeMillis());

        Path archiveFile = archiveDir(cid).resolve(meta.getFileName());
        writeJsonLines(archiveFile, copied);
        return meta;
    }

    @Override
    public void upsertVector(@NonNull String cid, @NonNull ChunkMeta chunkMeta, String chunkText, float[] vector) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        MemoryVectorDocument doc = new MemoryVectorDocument();
        doc.setChunkId(chunkMeta.getChunkId());
        doc.setText(chunkText);
        doc.setVector(vector);
        doc.setEndTs(chunkMeta.getEndTs());

        entity.getVectorDocuments().removeIf(item -> Objects.equals(item.getChunkId(), chunkMeta.getChunkId()));
        entity.getVectorDocuments().add(doc);
        persistVectors(cid, entity.getVectorDocuments());
    }

    @Override
    public @NonNull List<RetrievalChunk> searchSimilar(@NonNull String cid, float[] queryVector, int topK) {
        ConversationEntity entity = getConversationMemoryOrStorage(cid);
        List<RetrievalChunk> out = new ArrayList<>();

        for (MemoryVectorDocument doc : entity.getVectorDocuments()) {
            RetrievalChunk chunk = new RetrievalChunk();
            chunk.setChunkId(doc.getChunkId());
            chunk.setText(doc.getText());
            chunk.setEndTs(doc.getEndTs());
            chunk.setScore(cosine(queryVector, doc.getVector()));
            out.add(chunk);
        }

        out.sort(Comparator.comparingDouble(RetrievalChunk::getScore).reversed());
        return new ArrayList<>(out.subList(0, Math.min(topK, out.size())));
    }

    private @NonNull ConversationEntity loadConversationEntity(@NonNull String cid) {
        ConversationEntity entity = new ConversationEntity(cid);

        SessionMeta meta = readJson(sessionDir(cid).resolve(META_FILE), SessionMeta.class, new SessionMeta());
        meta.setSessionId(cid);
        entity.getMeta().setSessionId(cid);
        entity.getMeta().setLastChunkSeq(meta.getLastChunkSeq());
        entity.getMeta().setSummaryVersion(meta.getSummaryVersion());
        entity.getMeta().setVectorVersion(meta.getVectorVersion());
        entity.getMeta().setLastUpdatedAt(meta.getLastUpdatedAt());

        Facts facts = readJson(sessionDir(cid).resolve(FACTS_FILE), Facts.class, new Facts());
        entity.getFacts().setFacts(new LinkedHashMap<>(facts.getFacts()));

        SummaryState summary = readJson(sessionDir(cid).resolve(SUMMARY_FILE), SummaryState.class, new SummaryState());
        entity.getSummary().setEntries(new LinkedHashMap<>(summary.getEntries()));
        entity.getSummary().setUpdatedAt(summary.getUpdatedAt());

        entity.getMessages().addAll(readMessages(sessionDir(cid).resolve(RECENT_FILE)));
        entity.getVectorDocuments().addAll(readVectors(sessionDir(cid).resolve(VECTOR_DIR).resolve(VECTOR_FILE)));
        entity.getArchiveChunks().putAll(loadArchiveChunks(cid));
        return entity;
    }

    private void persistAll(String cid, @NonNull ConversationEntity entity) {
        persistMeta(cid, entity.getMeta());
        persistFacts(cid, entity.getFacts());
        persistSummary(cid, entity.getSummary());
        persistRecent(cid, entity.getMessages());
        persistVectors(cid, entity.getVectorDocuments());
    }

    private void persistMeta(String cid, @NonNull SessionMeta meta) {
        meta.setSessionId(cid);
        writeJson(sessionDir(cid).resolve(META_FILE), meta);
    }

    private void persistFacts(String cid, Facts facts) {
        writeJson(sessionDir(cid).resolve(FACTS_FILE), facts);
    }

    private void persistSummary(String cid, SummaryState summary) {
        writeJson(sessionDir(cid).resolve(SUMMARY_FILE), summary);
    }

    private void persistRecent(String cid, @Nullable List<Message> messages) {
        writeJsonLines(sessionDir(cid).resolve(RECENT_FILE), messages == null ? List.of() : messages);
    }

    private void persistVectors(String cid, @Nullable List<MemoryVectorDocument> docs) {
        writeJson(sessionDir(cid).resolve(VECTOR_DIR).resolve(VECTOR_FILE), docs == null ? List.of() : docs);
    }

    private @NonNull Map<String, List<Message>> loadArchiveChunks(String cid) {
        Map<String, List<Message>> archive = new LinkedHashMap<>();
        Path dir = archiveDir(cid);
        if (!Files.isDirectory(dir)) {
            return archive;
        }
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                    .sorted()
                    .forEach(path -> archive.put(stripJsonl(path.getFileName().toString()), readMessages(path)));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load archive chunks for cid=" + cid, ex);
        }
        return archive;
    }

    private List<MemoryVectorDocument> readVectors(@NonNull Path file) {
        try {
            if (!Files.exists(file)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(file.toFile(), new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read vector index: " + file, ex);
        }
    }

    private @NonNull List<Message> readMessages(@NonNull Path file) {
        List<Message> messages = new ArrayList<>();
        if (!Files.exists(file)) {
            return messages;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                StoredMessage stored = objectMapper.readValue(line, StoredMessage.class);
                messages.add(toMessage(stored));
            }
            return messages;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read conversation: " + file, ex);
        }
    }

    private void writeJsonLines(@NonNull Path file, @NonNull List<Message> messages) {
        mkdirs(file.getParent());
        List<String> lines = new ArrayList<>();
        try {
            for (Message message : messages) {
                lines.add(objectMapper.writeValueAsString(fromMessage(message)));
            }
            Files.write(file, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write message file: " + file, ex);
        }
    }

    private void writeJson(@NonNull Path file, Object value) {
        mkdirs(file.getParent());
        try {
            objectMapper.writeValue(file.toFile(), value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write json file: " + file, ex);
        }
    }

    private <T> T readJson(@NonNull Path file, Class<T> type, T defaultValue) {
        try {
            if (!Files.exists(file)) {
                return defaultValue;
            }
            return objectMapper.readValue(file.toFile(), type);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read json file: " + file, ex);
        }
    }

    private @NonNull Message toMessage(@Nullable StoredMessage stored) {
        if (stored == null || stored.role == null) {
            return new UserMessage("");
        }
        String text = stored.content == null ? "" : stored.content;
        return switch (stored.role.toLowerCase()) {
            case "assistant" -> new AssistantMessage(text);
            case "system" -> new SystemMessage(text);
            default -> new UserMessage(text);
        };
    }

    private @NonNull StoredMessage fromMessage(@Nullable Message message) {
        StoredMessage stored = new StoredMessage();
        stored.role = message == null
                ? "user"
                : message.getMessageType().name().toLowerCase();
        stored.content = message == null ? "" : message.getText();
        stored.ts = System.currentTimeMillis();
        return stored;
    }

    private double cosine(float @Nullable [] a, float @Nullable [] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return 0.0d;
        }
        int len = Math.min(a.length, b.length);
        double sum = 0.0d;
        for (int i = 0; i < len; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private long extractStartTs() {
        return System.currentTimeMillis();
    }

    private @NonNull Path sessionDir(String cid) {
        return baseDir.resolve(sanitizeCid(cid));
    }

    private @NonNull Path archiveDir(String cid) {
        Path dir = sessionDir(cid).resolve(ARCHIVE_DIR);
        mkdirs(dir);
        return dir;
    }

    private @NonNull String sanitizeCid(@Nullable String cid) {
        if (cid == null || cid.isBlank()) {
            return "default";
        }
        return cid.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private @NonNull String stripJsonl(@NonNull String name) {
        return name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name;
    }

    private void mkdirs(@Nullable Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create directory: " + dir, ex);
        }
    }

    private void deleteRecursively(@Nullable Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.list(path)) {
                    stream.forEach(this::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to delete path: " + path, ex);
        }
    }

    private static class StoredMessage {
        public String role;
        public @Nullable String content;
        public long ts;
    }
}
