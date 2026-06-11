package com.shopee.queue.storage;

import com.shopee.queue.BrokerMain;
import com.shopee.queue.api.IStorageManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The Orchestrator for the Storage Layer.
 * Manages segment allocations and exposes API entries for both standard local writes
 * and incoming replicated cluster logs.
 */
public class StorageManagerImpl implements IStorageManager {

    private final String rootDirectory = "data/";
    private static final long MAX_SEGMENT_SIZE = 1024 * 1024 * 1024L; // 1GB
    private final Map<String, TopicStorage> topicStorageMap = new ConcurrentHashMap<>();

    public StorageManagerImpl() {
        File rootDir = new File(rootDirectory);
        if (!rootDir.exists()) {
            rootDir.mkdirs();
        }

        File[] topics = rootDir.listFiles(File::isDirectory);
        if (topics != null) {
            for (File topicDir : topics) {
                String topicName = topicDir.getName();
                try {
                    topicStorageMap.put(topicName, new TopicStorage(topicName, rootDirectory));
                    System.out.println("[Storage] Recovered topic log metadata: " + topicName);
                } catch (IOException e) {
                    System.err.println("Failed to recover topic log metadata: " + topicName);
                }
            }
        }
    }

    /**
     * Queries the actual, physical byte size of the active log segment on disk.
     */
    public double getActiveSegmentFillRatio(String topic) {
        TopicStorage storage = topicStorageMap.get(topic);
        if (storage == null) return 0.0;
        try {
            return (double) storage.getActiveSegment().logSegment.getCurrentPosition() / MAX_SEGMENT_SIZE;
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Queries the true logical offset index count currently stored on disk.
     */
    public long getGlobalOffsetCount(String topic) {
        TopicStorage storage = topicStorageMap.get(topic);
        return (storage != null) ? storage.globalOffsetCounter.get() : 0;
    }

    /**
     * FIXED: Queries the total number of physical segment files (log/index pairs) allocated for a topic.
     */
    public int getSegmentCount(String topic) {
        TopicStorage storage = topicStorageMap.get(topic);
        return (storage != null) ? storage.segments.size() : 1;
    }

    /**
     * Entry point for standard Leader writes. Generates a new global offset sequentially.
     */
    public long appendToLog(String topic, byte[] data) throws IOException {
        TopicStorage storage = topicStorageMap.computeIfAbsent(topic, this::createTopicStorage);

        synchronized (storage) {
            SegmentPair activeSegment = storage.getActiveSegment();

            // Segment Rotation Check
            if (activeSegment.logSegment.getCurrentPosition() + data.length > MAX_SEGMENT_SIZE) {
                activeSegment.close();
                activeSegment = storage.createNewSegment();
            }

            long globalOffset = storage.globalOffsetCounter.getAndIncrement();
            long relativeOffset = globalOffset - activeSegment.startOffset;

            long physicalPosition = activeSegment.logSegment.append(data);
            activeSegment.indexSegment.addEntry(relativeOffset, physicalPosition, data.length);

            // Broadcast log write to UI bridge
            if (BrokerMain.bridge != null) {
                double fillRatio = (double) activeSegment.logSegment.getCurrentPosition() / MAX_SEGMENT_SIZE;
                BrokerMain.bridge.emitAppend(topic, globalOffset, fillRatio);
            }

            return globalOffset;
        }
    }

    /**
     * Entry point for Followers. Writes replicated data at the exact same offset assigned
     * by the Leader to prevent log drifting across the cluster.
     */
    public void appendReplicatedEntry(String topic, long offset, byte[] data) throws IOException {
        TopicStorage storage = topicStorageMap.computeIfAbsent(topic, this::createTopicStorage);

        synchronized (storage) {
            SegmentPair activeSegment = storage.getActiveSegment();

            // Segment Rotation Check
            if (activeSegment.logSegment.getCurrentPosition() + data.length > MAX_SEGMENT_SIZE) {
                activeSegment.close();
                activeSegment = storage.createNewSegment();
            }

            // Force align follow counter with the Leader's designated offset
            storage.globalOffsetCounter.set(offset);
            long relativeOffset = offset - activeSegment.startOffset;

            long physicalPosition = activeSegment.logSegment.append(data);
            activeSegment.indexSegment.addEntry(relativeOffset, physicalPosition, data.length);

            // Align counter for subsequent appends
            storage.globalOffsetCounter.set(offset + 1);

            // Broadcast follow write to UI bridge
            if (BrokerMain.bridge != null) {
                double fillRatio = (double) activeSegment.logSegment.getCurrentPosition() / MAX_SEGMENT_SIZE;
                BrokerMain.bridge.emitAppend(topic, offset, fillRatio);
            }
        }
    }

    public byte[] readFromOffset(String topic, long offset) throws IOException {
        TopicStorage storage = topicStorageMap.get(topic);
        if (storage == null) return null;

        SegmentPair segment = storage.findSegmentForOffset(offset);
        if (segment == null) return null;

        long relativeOffset = offset - segment.startOffset;
        IndexSegment.IndexData indexData = segment.indexSegment.getIndexData(relativeOffset);
        if (indexData == null) return null;

        return segment.logSegment.read(indexData.physicalPosition, indexData.messageLength);
    }

    private TopicStorage createTopicStorage(String topic) {
        try {
            return new TopicStorage(topic, rootDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage for topic: " + topic, e);
        }
    }

    private static class SegmentPair {
        long startOffset;
        LogSegment logSegment;
        IndexSegment indexSegment;

        public void close() throws IOException {
            logSegment.close();
            indexSegment.close();
        }
    }

    private static class TopicStorage {
        final String topicDir;
        final List<SegmentPair> segments = new java.util.concurrent.CopyOnWriteArrayList<>();
        final AtomicLong globalOffsetCounter = new AtomicLong(0);

        public TopicStorage(String topic, String rootDir) throws IOException {
            this.topicDir = rootDir + topic + "/";
            File dir = new File(this.topicDir);
            if (!dir.exists()) dir.mkdirs();

            File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
            if (files != null && files.length > 0) {
                java.util.Arrays.sort(files);
                for (File file : files) {
                    String baseName = file.getAbsolutePath().replace(".log", "");
                    String fileNameOnly = file.getName().replace(".log", "");
                    long startOff = Long.parseLong(fileNameOnly);

                    SegmentPair pair = new SegmentPair();
                    pair.startOffset = startOff;
                    pair.logSegment = new LogSegment(baseName + ".log");
                    pair.indexSegment = new IndexSegment(baseName + ".index");
                    segments.add(pair);
                }

                SegmentPair lastSegment = getActiveSegment();
                long nextId = lastSegment.startOffset + lastSegment.indexSegment.getEntryCount();
                globalOffsetCounter.set(nextId);
            } else {
                createNewSegment();
            }
        }

        public SegmentPair getActiveSegment() {
            return segments.get(segments.size() - 1);
        }

        public SegmentPair createNewSegment() throws IOException {
            long newStartOffset = globalOffsetCounter.get();
            String baseName = topicDir + String.format("%020d", newStartOffset);

            SegmentPair pair = new SegmentPair();
            pair.startOffset = newStartOffset;
            pair.logSegment = new LogSegment(baseName + ".log");
            pair.indexSegment = new IndexSegment(baseName + ".index");

            segments.add(pair);
            return pair;
        }

        public SegmentPair findSegmentForOffset(long offset) {
            for (int i = segments.size() - 1; i >= 0; i--) {
                if (offset >= segments.get(i).startOffset) {
                    return segments.get(i);
                }
            }
            return null;
        }
    }

    @Override
    public void shutdown() {
        System.out.println("StorageManager: Flushing and closing all files...");
        for (TopicStorage storage : topicStorageMap.values()) {
            for (SegmentPair segment : storage.segments) {
                try {
                    segment.close();
                } catch (IOException e) {
                    System.err.println("Failed to close segment: " + e.getMessage());
                }
            }
        }
        System.out.println("StorageManager: All files closed safely.");
    }
}