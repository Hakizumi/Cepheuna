package org.sempiria.cepheuna.memory.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Retrieval chunk dto.
 * ( The chunk has value )
 *
 * @since 1.3.0-beta
 * @version 1.0.0
 * @author Sempiria
 */
@Getter
@Setter
public class RetrievalChunk {
    private String chunkId;
    private String text;
    private double score;
    private long endTs;
}
