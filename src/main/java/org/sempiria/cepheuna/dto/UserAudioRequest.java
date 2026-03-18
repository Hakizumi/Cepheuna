package org.sempiria.cepheuna.dto;

/**
 * User or assistant audio/conversation segment DTO.
 *
 * @param text conversation content
 * @param cid conversation id
 * @param utteranceId assistant utterance id, nullable for STT events
 * @param segmentIndex segment index in a single utterance, 0-based
 *
 * @since 1.0.0
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
