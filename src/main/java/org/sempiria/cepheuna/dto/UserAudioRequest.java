package org.sempiria.cepheuna.dto;

/**
 * User or assistant audio/text segment DTO.
 *
 * @param text text content
 * @param cid conversation id
 * @param utteranceId assistant utterance id, nullable for STT events
 * @param segmentIndex segment index in a single utterance, 0-based
 *
 * @since 3.0.2
 * @version 1.0.0
 * @author Sempiria
 */
public record UserAudioRequest(
        String text,
        String cid,
        String utteranceId,
        long segmentIndex
) {
}
