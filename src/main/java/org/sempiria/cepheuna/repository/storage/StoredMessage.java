package org.sempiria.cepheuna.repository.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Stored message dto.
 *
 * @since 1.3.0
 * @version 1.3.0
 * @author Sempiria
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class StoredMessage {
    @JsonProperty("messageType")
    public String role;
    public Map<String,String> metadata;
    public @Nullable String text;
}
