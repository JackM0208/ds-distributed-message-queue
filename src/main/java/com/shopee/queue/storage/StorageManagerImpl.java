package com.shopee.queue.storage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.shopee.queue.api.IStorageManager;

/**
 * The Orchestrator for the Storage Layer.
 * Manages directories, segment rotation, and routes read/write requests
 * to the correct Log and Index segments for each topic.
 */
public class StorageManagerImpl implements IStorageManager {

    // The root directory where all data is stored
    private final String rootDirectory = "data/";

    // Max size of a single .log file before we create a new one (e.g., 1GB)
    private static final long MAX_SEGMENT_SIZE = 1024 * 1024 * 1024L;

    // A Map holding the storage state for every Topic (e.g., "orders" -> TopicStorage)
    private final Map<String, TopicStorage> topicStorageMap = new ConcurrentHashMap<>();

    public StorageManagerImpl() {
        File rootDir = new File(rootDirectory);
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }
        startBackgroundFlush();
    }

    private void startBackgroundFlush() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            for (String topic : topicStorageMap.keySet()) {
                flush(topic);
            }
        }, 500, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * 3. Orchestrates writing data to the correct file.
     * @param topic The topic to write to (e.g., "orders").
     * @param data The raw message bytes.
     * @return The globally assigned offset (Message ID) for this data.
     */
    public long appendToLog(String topic, byte[] data) throws IOException {
        // 1. Get or create the topic directory and state
        TopicStorage storage = topicStorageMap.computeIfAbsent(topic, this::createTopicStorage);

        synchronized (storage) {
            SegmentPair activeSegment = storage.getActiveSegment();

            // 2. Segment Rotation: Check if the current file is too full
            if (activeSegment.logSegment.getCurrentPosition() + data.length > MAX_SEGMENT_SIZE) {
                // Close current files and create new ones
                activeSegment.close();
                activeSegment = storage.createNewSegment();
            }

            // Assign a new global offset (Message ID)
            long globalOffset = storage.globalOffsetCounter.getAndIncrement();

            // Calculate relative offset for the Index (e.g., Global 1050 - SegmentStart 1000 = Relative 50)
            long relativeOffset = globalOffset - activeSegment.startOffset;

            // --- THE WRITE WORKFLOW ---
            // A. Write to Log and get the physical position
            long physicalPosition = activeSegment.logSegment.append(data);

            // B. Write to the Smart Index (ID, Position, Length)
            activeSegment.indexSegment.addEntry(relativeOffset, physicalPosition, data.length);

            return globalOffset;
        }
    }

    /**
     * 4. Orchestrates reading data by finding the correct segment.
     * @param topic The topic to read from.
     * @param offset The global Message ID.
     * @return The raw bytes of the message, or null if not found.
     */
    public byte[] readFromOffset(String topic, long offset) throws IOException {
        TopicStorage storage = topicStorageMap.get(topic);
        if (storage == null) return null; // Topic doesn't exist

        // 1. Find which segment contains this offset
        SegmentPair segment = storage.findSegmentForOffset(offset);
        if (segment == null) return null; // Offset is too old or doesn't exist

        // Calculate relative offset
        long relativeOffset = offset - segment.startOffset;

        // --- THE READ WORKFLOW ---
        // A. Ask the Index for the GPS Coordinates and Length
        IndexSegment.IndexData indexData = segment.indexSegment.getIndexData(relativeOffset);
        if (indexData == null) return null; // Message not written yet

        // B. Ask the Log for the actual bytes using the exact Position and Length
        return segment.logSegment.read(indexData.physicalPosition, indexData.messageLength);
    }

    @Override
    public void flush(String topic) {
        TopicStorage storage = topicStorageMap.get(topic);
        if (storage != null) {
            try {
                storage.getActiveSegment().logSegment.flush();
            } catch (IOException e) {
                // Use a proper logger in a real system
                System.err.println("Failed to flush topic: " + topic);
            }
        }
    }

    /**
     * Gracefully closes all segments and releases file locks.
     */
    public void close() throws IOException {
        for (TopicStorage storage : topicStorageMap.values()) {
            synchronized (storage) {
                for (SegmentPair segment : storage.segments) {
                    segment.close();
                }
            }
        }
    }


    // ====================================================================================
    // HELPER CLASSES AND METHODS
    // ====================================================================================

    private TopicStorage createTopicStorage(String topic) {
        try {
            return new TopicStorage(topic, rootDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage for topic: " + topic, e);
        }
    }

    /**
     * Groups a LogSegment and an IndexSegment together.
     */
    private static class SegmentPair {
        long startOffset; // The global message ID this segment starts with
        LogSegment logSegment;
        IndexSegment indexSegment;

        public void close() throws IOException {
            logSegment.close();
            indexSegment.close();
        }
    }

    /**
     * Manages all the segments for a single topic.
     */
    private static class TopicStorage {
        final String topicDir;
        final List<SegmentPair> segments = new ArrayList<>();
        final AtomicLong globalOffsetCounter = new AtomicLong(0);

        public TopicStorage(String topic, String rootDir) throws IOException {
            this.topicDir = rootDir + topic + "/";
            File dir = new File(this.topicDir);
            if (!dir.exists()) dir.mkdirs();

            // Start with a fresh segment
            createNewSegment();
        }

        public SegmentPair getActiveSegment() {
            return segments.get(segments.size() - 1); // The last segment is the active one
        }

        public SegmentPair createNewSegment() throws IOException {
            long newStartOffset = globalOffsetCounter.get();

            // File names are usually padded with zeros to represent their starting offset
            // e.g., "00000000000000001000.log"
            String baseName = topicDir + String.format("%020d", newStartOffset);

            SegmentPair pair = new SegmentPair();
            pair.startOffset = newStartOffset;
            pair.logSegment = new LogSegment(baseName + ".log");
            pair.indexSegment = new IndexSegment(baseName + ".index");

            segments.add(pair);
            return pair;
        }

        public SegmentPair findSegmentForOffset(long offset) {
            // Simple linear search. (In a real system with thousands of segments, use Binary Search)
            for (int i = segments.size() - 1; i >= 0; i--) {
                if (offset >= segments.get(i).startOffset) {
                    return segments.get(i);
                }
            }
            return null; // Offset is smaller than our oldest segment (perhaps it was deleted)
        }
    }
}