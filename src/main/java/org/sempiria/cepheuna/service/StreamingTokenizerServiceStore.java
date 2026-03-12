package org.sempiria.cepheuna.service;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-conversation tokenizer store.
 *
 * @since 1.0.0
 * @version 1.0.0
 * @author Sempiria
 */
@Component
public class StreamingTokenizerServiceStore {
    private final AutowireCapableBeanFactory beanFactory;
    private final ConcurrentHashMap<String, StreamingTokenizerService> cache = new ConcurrentHashMap<>();

    public StreamingTokenizerServiceStore(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    public @NonNull StreamingTokenizerService getInstance(String cid) {
        return cache.computeIfAbsent(cid, (k) -> beanFactory.createBean(StreamingTokenizerService.class));
    }

    public void reset(String cid) {
        StreamingTokenizerService service = cache.get(cid);
        if (service != null) {
            service.reset();
        }
    }

    public void remove(@NonNull String cid) {
        cache.remove(cid);
    }
}
