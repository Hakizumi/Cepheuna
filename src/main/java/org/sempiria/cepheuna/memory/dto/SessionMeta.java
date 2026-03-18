package org.sempiria.cepheuna.memory.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Conversation metadata dto.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Getter
@Setter
public class SessionMeta {
    private String sessionId;
    private int lastChunkSeq;
    private int summaryVersion;
    private int vectorVersion;
    private long lastUpdatedAt;

    public void addLastChunkSeq() {
        lastChunkSeq++;
    }

    public void addSummaryVersion() {
        summaryVersion++;
    }

    public void addVectorVersion() {
        vectorVersion++;
    }
}
