package com.shopee.queue.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Manages a single physical index file on the disk (e.g., 000000.index).
 * Acts as the "Smart Bookmark", allowing the Broker to instantly
 * find both the byte position AND the size of a message without scanning.
 */
public class IndexSegment {

    // UPDATE 1: Size increased from 16 to 20 bytes
    // 8 bytes (Offset) + 8 bytes (Physical Position) + 4 bytes (Length Integer)
    private static final int ENTRY_SIZE = 20;

    // 10 MB limit (can now hold ~524,000 entries of 20 bytes each)
    private static final int MAX_INDEX_SIZE = 10 * 1024 * 1024;

    private final String filePath;
    private final RandomAccessFile randomAccessFile;
    private final FileChannel fileChannel;
    private final MappedByteBuffer mappedByteBuffer;
    private int writePosition = 0; // Tracks the next empty spot in the buffer

    /**
     * UPDATE 2: A helper class to return multiple values at once.
     * Since Java can't return two separate numbers easily, we pack them together.
     * MAYBE THIS NEEDS TO BE REFACTOR
     */
    public static class IndexData {
        public final long physicalPosition;
        public final int messageLength;

        public IndexData(long physicalPosition, int messageLength) {
            this.physicalPosition = physicalPosition;
            this.messageLength = messageLength;
        }
    }

    public IndexSegment(String filePath) throws IOException {
        this.filePath = filePath;
        File file = new File(filePath);

        this.randomAccessFile = new RandomAccessFile(file, "rw");
        this.fileChannel = this.randomAccessFile.getChannel();

        this.mappedByteBuffer = this.fileChannel.map(FileChannel.MapMode.READ_WRITE, 0, MAX_INDEX_SIZE);

        this.writePosition = (int) this.fileChannel.size();
        this.mappedByteBuffer.position(this.writePosition);
    }

    /**
     * UPDATE 3: Added the messageLength parameter.
     * Writes a new mapping (Message Offset -> Log Physical Position + Length).
     */
    public synchronized void addEntry(long messageOffset, long physicalPosition, int messageLength) {
        // We now write exactly 20 bytes:
        this.mappedByteBuffer.putLong(messageOffset);     // 8 bytes
        this.mappedByteBuffer.putLong(physicalPosition);  // 8 bytes
        this.mappedByteBuffer.putInt(messageLength);      // 4 bytes (an integer is 4 bytes)

        // Increment the write tracker by 20
        this.writePosition += ENTRY_SIZE;
    }

    /**
     * UPDATE 4: Retrieves both the position and the length.
     * @param relativeOffset The relative message number in this file (e.g., 100).
     * @return An IndexData object holding position and length, or null if not found.
     */
    public IndexData getIndexData(long relativeOffset) {
        // Calculate exactly where this entry lives in the file (offset * 20)
        int targetByteLocation = (int) (relativeOffset * ENTRY_SIZE);

        // Safety check: Ensure we don't read past what we've written
        if (targetByteLocation >= this.writePosition) {
            return null; // Message does not exist yet
        }

        // --- STEP 1: Skip the ID and read the Position ---
        // Jump past the first 8 bytes (the messageOffset)
        int positionDataLocation = targetByteLocation + 8;
        long physicalPosition = this.mappedByteBuffer.getLong(positionDataLocation);

        // --- STEP 2: Skip the Position and read the Length ---
        // Jump past the 8-byte Position we just read
        int lengthDataLocation = positionDataLocation + 8;
        int messageLength = this.mappedByteBuffer.getInt(lengthDataLocation);

        // Return both values wrapped in our helper class
        return new IndexData(physicalPosition, messageLength);
    }

    public void flush() {
        if (this.mappedByteBuffer != null) {
            this.mappedByteBuffer.force();
        }
    }

    public void close() throws IOException {
        flush();
        if (this.fileChannel != null) {
            this.fileChannel.close();
        }
        if (this.randomAccessFile != null) {
            this.randomAccessFile.close();
        }
    }
}