package org.sempiria.cepheuna.memory.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Chunk metadata dto
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Getter
@Setter
public class ChunkMeta {
    private String chunkId;
    private long startTs;
    private long endTs;
    private int messageCount;
    private String fileName;
}
