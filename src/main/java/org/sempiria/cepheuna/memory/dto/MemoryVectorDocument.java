package org.sempiria.cepheuna.memory.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Conversation memory embedded vectors dto.
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Getter
@Setter
public class MemoryVectorDocument {
    private String chunkId;
    private String text;
    private float[] vector;
    private long endTs;
}
