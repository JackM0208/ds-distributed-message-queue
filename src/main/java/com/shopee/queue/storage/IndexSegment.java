package com.shopee.queue.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Manages a single physical index file on the disk.
 * Rewritten to use safe FileChannel I/O to avoid MappedByteBuffer padding bugs.
 */
public class IndexSegment {

    private static final int ENTRY_SIZE = 20;

    private final String filePath;
    private final RandomAccessFile randomAccessFile;
    private final FileChannel fileChannel;
    private long writePosition = 0;

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

        // The logical write position is simply the current true size of the file
        this.writePosition = this.fileChannel.size();
        this.fileChannel.position(this.writePosition);
    }

    public synchronized void addEntry(long messageOffset, long physicalPosition, int messageLength) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(ENTRY_SIZE);
        buffer.putLong(messageOffset);
        buffer.putLong(physicalPosition);
        buffer.putInt(messageLength);
        buffer.flip();

        while (buffer.hasRemaining()) {
            this.fileChannel.write(buffer);
        }
        this.writePosition += ENTRY_SIZE;
    }

    public IndexData getIndexData(long relativeOffset) throws IOException {
        long targetByteLocation = relativeOffset * ENTRY_SIZE;

        if (targetByteLocation >= this.writePosition) {
            return null; // Message does not exist yet
        }

        ByteBuffer buffer = ByteBuffer.allocate(ENTRY_SIZE);
        int bytesRead = this.fileChannel.read(buffer, targetByteLocation);

        if (bytesRead < ENTRY_SIZE) {
            return null;
        }

        buffer.flip();
        long offset = buffer.getLong(); // skip the first 8 bytes
        long physicalPosition = buffer.getLong(); // next 8 bytes
        int messageLength = buffer.getInt(); // final 4 bytes

        return new IndexData(physicalPosition, messageLength);
    }

    public void flush() throws IOException {
        if (this.fileChannel != null) {
            this.fileChannel.force(true);
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

    public int getEntryCount() {
        return (int) (this.writePosition / ENTRY_SIZE);
    }
}