package com.shopee.queue.storage;

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
 * Purpose: Acts as the entry point for all disk operations. It hides the complexity
 * of file management and segment rotation from the rest of the MQ system.
 */
public class StorageManagerImpl implements IStorageManager {

    // The folder where everything is saved
    private final String rootDirectory = "data/";

    // 1GB limit. Prevents any single file from becoming a "Corrupted Giant"
    // that is impossible to move or repair.
    private static final long MAX_SEGMENT_SIZE = 1024 * 1024 * 1024L;

    // Thread-safe map: Key is Topic Name, Value is the "Manager" for that topic
    private final Map<String, TopicStorage> topicStorageMap = new ConcurrentHashMap<>();

    public StorageManagerImpl() {
        // Step 1: Ensure the "data/" directory exists on the hard drive
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
                    System.out.println("[Storage] Recovered topic: " + topicName);
                } catch (IOException e) {
                    System.err.println("Failed to recover topic " + topicName);
                }
            }
        }
    }

    /**
     * Entry point for Producers. Saves a message to disk.
     */
    public long appendToLog(String topic, byte[] data) throws IOException {
        // 1. Find the storage room for this specific topic
        TopicStorage storage = topicStorageMap.computeIfAbsent(topic, this::createTopicStorage);

        // 2. Lock the topic. We can't have two threads trying to "Rotate" the file at the same time.
        synchronized (storage) {
            SegmentPair activeSegment = storage.getActiveSegment();

            // 3. SEGMENT ROTATION: Is the current file full?
            if (activeSegment.logSegment.getCurrentPosition() + data.length > MAX_SEGMENT_SIZE) {
                activeSegment.close(); // Seal the old files
                activeSegment = storage.createNewSegment(); // Open a brand new Log and Index
            }

            // 4. Assign the Message ID
            long globalOffset = storage.globalOffsetCounter.getAndIncrement();

            // 5. Calculate Local ID (If global is 1050 and file starts at 1000, local is 50)
            long relativeOffset = globalOffset - activeSegment.startOffset;

            // 6. WRITE DATA: Save raw bytes to Log, then save bookmark to Index
            long physicalPosition = activeSegment.logSegment.append(data);
            activeSegment.indexSegment.addEntry(relativeOffset, physicalPosition, data.length);

            return globalOffset; // Return the ID so the Producer knows it's safe
        }
    }

    /**
     * Entry point for Consumers. Retrieves a message by its ID.
     */
    public byte[] readFromOffset(String topic, long offset) throws IOException {
        TopicStorage storage = topicStorageMap.get(topic);
        if (storage == null) return null;

        // 1. FIND THE BOOK: Search through segments to find which one contains this ID
        SegmentPair segment = storage.findSegmentForOffset(offset);
        if (segment == null) return null;

        long relativeOffset = offset - segment.startOffset;

        // 2. FIND THE BOOKMARK: Ask the index where the data is
        IndexSegment.IndexData indexData = segment.indexSegment.getIndexData(relativeOffset);
        if (indexData == null) return null;

        // 3. GET THE DATA: Go to the Log at that exact "GPS Coordinate" and length
        return segment.logSegment.read(indexData.physicalPosition, indexData.messageLength);
    }

    private TopicStorage createTopicStorage(String topic) {
        try {
            return new TopicStorage(topic, rootDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage for topic: " + topic, e);
        }
    }

    /**
     * Inner Class: Groups a Log and Index together as a single logical unit.
     */
    private static class SegmentPair {
        long startOffset; // What ID did this file start with? (e.g. 1000)
        LogSegment logSegment;
        IndexSegment indexSegment;

        public void close() throws IOException {
            logSegment.close();
            indexSegment.close();
        }
    }

    /**
     * Inner Class: Manages the collection of "Books" (Segments) for a specific Topic.
     */
    private static class TopicStorage {
        final String topicDir;
        final List<SegmentPair> segments = new ArrayList<>();
        final AtomicLong globalOffsetCounter = new AtomicLong(0);

        public TopicStorage(String topic, String rootDir) throws IOException {
            this.topicDir = rootDir + topic + "/";
            File dir = new File(this.topicDir);
            if (!dir.exists()) dir.mkdirs();

            // RECOVERY LOGIC: Scan existing files
            File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
            if (files != null && files.length > 0) {
                java.util.Arrays.sort(files); // Sort alphabetically to get chronological order

                for (File file : files) {
                    String baseName = file.getAbsolutePath().replace(".log", "");
                    String fileNameOnly = file.getName().replace(".log", "");
                    long startOff = Long.parseLong(fileNameOnly); // Parse 0000001000 to 1000

                    SegmentPair pair = new SegmentPair();
                    pair.startOffset = startOff;
                    pair.logSegment = new LogSegment(baseName + ".log");
                    pair.indexSegment = new IndexSegment(baseName + ".index");
                    segments.add(pair);
                }

                // Set counter to the END of the very last file
                SegmentPair lastSegment = getActiveSegment();
                long nextId = lastSegment.startOffset + lastSegment.indexSegment.getEntryCount();
                globalOffsetCounter.set(nextId);
            } else {
                // Brand new topic
                createNewSegment();
            }
        }

        public SegmentPair getActiveSegment() {
            return segments.get(segments.size() - 1);
        }

        public SegmentPair createNewSegment() throws IOException {
            long newStartOffset = globalOffsetCounter.get();
            // Name the file after its start ID (e.g., 00000000000000001000.log)
            String baseName = topicDir + String.format("%020d", newStartOffset);

            SegmentPair pair = new SegmentPair();
            pair.startOffset = newStartOffset;
            pair.logSegment = new LogSegment(baseName + ".log");
            pair.indexSegment = new IndexSegment(baseName + ".index");

            segments.add(pair);
            return pair;
        }

        public SegmentPair findSegmentForOffset(long offset) {
            // Searches backwards from the newest book to the oldest
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