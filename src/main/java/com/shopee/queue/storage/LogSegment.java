package com.shopee.queue.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Manages a single log segment file.
 * Utilizes Java NIO's {@link FileChannel} for high-performance I/O.
 * Implements "Append-Only" writes, ensuring data is only written at the end 
 * of the file to maintain high sequential write speeds.
 */
public class LogSegment {
    private final String filePath;
    private final RandomAccessFile randomAccessFile;
    private final FileChannel fileChannel;
    private long currentPosition;//this is store in the number of bytes

    public LogSegment(String filePath) throws IOException {
        this.filePath = filePath;
        File file = new File(filePath);

        //Open the file in "rw" mode
        this.randomAccessFile = new RandomAccessFile(file, "rw"); //symbolic vs hardlink?
        this.fileChannel = randomAccessFile.getChannel();

        //maintain the current position, starting at the end of the file
        this.currentPosition = this.fileChannel.size();
        this.fileChannel.position(this.currentPosition);
    }

    public synchronized long append(byte[] data) throws IOException {
        long writePosition = this.currentPosition;

        ByteBuffer byteBuffer = ByteBuffer.wrap(data);

        while (byteBuffer.hasRemaining()) {
            fileChannel.write(byteBuffer);
        }
        this.currentPosition += data.length;
        return writePosition;
    }

    public byte[] read(long position, int length) throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.allocate(length);
        int bytesRead = this.fileChannel.read(byteBuffer, position);
        if (bytesRead == -1) {
            return null;
        }
        return byteBuffer.array();
    }

    /**
     * Closes the file resources gracefully when the Broker shuts down or the file gets too big.
     */
    public void close() throws IOException {
        if (fileChannel != null) {
            fileChannel.close();
        }
        if (randomAccessFile != null) {
            randomAccessFile.close();
        }
    }

    public long getCurrentPosition() {
        return this.currentPosition;
    }


}
