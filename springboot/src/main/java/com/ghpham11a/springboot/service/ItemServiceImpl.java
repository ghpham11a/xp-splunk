package com.ghpham11a.springboot.service;

import com.ghpham11a.springboot.dto.CreateItemRequest;
import com.ghpham11a.springboot.dto.UpdateItemRequest;
import com.ghpham11a.springboot.exception.ItemNotFoundException;
import com.ghpham11a.springboot.model.Item;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ItemServiceImpl implements ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemServiceImpl.class);

    private final Map<String, Item> store = new ConcurrentHashMap<>();
    private final MeterRegistry registry;

    private final AtomicLong totalNameLength = new AtomicLong(0);
    private final DistributionSummary nameLengthSummary;
    private final DistributionSummary createPayloadSize;

    public ItemServiceImpl(MeterRegistry registry) {
        this.registry = registry;

        registry.gauge("items.count", store, Map::size);
        registry.gauge("items.total_name_length", totalNameLength);

        this.nameLengthSummary = DistributionSummary.builder("items.name_length")
                .description("Distribution of item name lengths")
                .baseUnit("characters")
                .publishPercentiles(0.5, 0.75, 0.95)
                .register(registry);

        this.createPayloadSize = DistributionSummary.builder("items.create_payload_size")
                .description("Estimated payload size of created items")
                .baseUnit("bytes")
                .publishPercentiles(0.5, 0.95)
                .register(registry);
    }

    @Override
    public List<Item> findAll() {
        return timer("find_all", "read").record(() -> {
            log.debug("Listing all items, count={}", store.size());
            return new ArrayList<>(store.values());
        });
    }

    @Override
    public Item findById(String id) {
        return timer("find_by_id", "read").record(() -> {
            Item item = store.get(id);
            if (item == null) {
                counter("not_found", "read").increment();
                log.warn("Item not found: id={}", id);
                throw new ItemNotFoundException(id);
            }
            log.debug("Found item: id={}", id);
            return item;
        });
    }

    @Override
    public Item create(CreateItemRequest request) {
        Item item = new Item(request.getName(), request.getDescription());
        store.put(item.getId(), item);
        counter("created", "write").increment();

        if (item.getName() != null) {
            nameLengthSummary.record(item.getName().length());
            totalNameLength.addAndGet(item.getName().length());
        }
        createPayloadSize.record(estimateSize(item));

        log.info("Created item: id={}, name={}", item.getId(), item.getName());
        return item;
    }

    @Override
    public Item update(String id, UpdateItemRequest request) {
        Item existing = store.get(id);
        if (existing == null) {
            counter("not_found", "write").increment();
            log.warn("Item not found for update: id={}", id);
            throw new ItemNotFoundException(id);
        }

        if (existing.getName() != null) {
            totalNameLength.addAndGet(-existing.getName().length());
        }
        if (request.getName() != null) {
            totalNameLength.addAndGet(request.getName().length());
            nameLengthSummary.record(request.getName().length());
        }

        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setUpdatedAt(Instant.now());
        counter("updated", "write").increment();

        log.info("Updated item: id={}", id);
        return existing;
    }

    @Override
    public void delete(String id) {
        Item removed = store.remove(id);
        if (removed == null) {
            counter("not_found", "write").increment();
            log.warn("Item not found for delete: id={}", id);
            throw new ItemNotFoundException(id);
        }

        if (removed.getName() != null) {
            totalNameLength.addAndGet(-removed.getName().length());
        }
        counter("deleted", "write").increment();

        log.info("Deleted item: id={}, name={}", id, removed.getName());
    }

    private Counter counter(String operation, String type) {
        return Counter.builder("items.operations")
                .description("Item operation count")
                .tags(Tags.of("operation", operation, "type", type))
                .register(registry);
    }

    private Timer timer(String operation, String type) {
        return Timer.builder("items.operation_duration")
                .description("Item operation duration")
                .tags(Tags.of("operation", operation, "type", type))
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    private long estimateSize(Item item) {
        long size = 0;
        if (item.getName() != null) size += item.getName().length();
        if (item.getDescription() != null) size += item.getDescription().length();
        if (item.getId() != null) size += item.getId().length();
        return size * 2;
    }
}
