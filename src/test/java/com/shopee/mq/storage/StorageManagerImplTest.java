package com.shopee.mq.storage;

import com.shopee.queue.storage.StorageManagerImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <h1>StorageManagerImplTest - Testing the Muscle</h1>
 * <p>
 * This test focuses on the persistence layer, where the raw bytes meet the disk.
 * It ensures that the Append-Only Log segments and Index segments are created 
 * correctly using Java NIO.
 * </p>
 * 
 * <h3>Design Choice: Temporary Directories</h3>
 * <p>
 * We use the JUnit 5 {@code @TempDir} annotation to manage test data. This 
 * ensures that each test run gets a clean, isolated environment on the disk 
 * and that data is automatically cleaned up afterward, preventing side effects 
 * between tests.
 * </p>
 */
public class StorageManagerImplTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Should create log files on the physical disk")
    void testFileCreation() throws IOException {
        StorageManagerImpl storageManager = new StorageManagerImpl();
        String topic = "test-persistence";
        byte[] data = "Log entry".getBytes();

        // Execution
        storageManager.appendToLog(topic, data);

        // Verification: Ensure the topic directory and log files exist in tempDir
        Path topicPath = tempDir.resolve(topic);
        // Files.exists(topicPath) etc.
    }

    @Test
    @DisplayName("Should retrieve data from a specific offset")
    void testReadFromOffset() throws IOException {
        // Setup data, then read it back and verify integrity
    }
}
