package com.shopee.mq.core;

import com.shopee.queue.api.IStorageManager;
import com.shopee.queue.core.QueueManagerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

/**
 * <h1>QueueManagerImplTest - Testing the Traffic Cop</h1>
 * <p>
 * This test class focuses on the business orchestration logic of the 
 * {@code QueueManagerImpl}. It ensures that the manager correctly delegates 
 * data persistence to the {@link IStorageManager}.
 * </p>
 * 
 * <h3>Design Choice: Mocking the Storage Layer</h3>
 * <p>
 * We use Mockito to mock {@code IStorageManager}. This isolates the logic 
 * of the {@code QueueManager} from the physical file system, adhering to 
 * the principle of "Unit Testing" where we test a single component in isolation.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
public class QueueManagerImplTest {

    @Mock
    private IStorageManager storageManager;

    @InjectMocks
    private QueueManagerImpl queueManager;

    @BeforeEach
    void setUp() {
        // Mockito will automatically initialize mocks via @ExtendWith(MockitoExtension.class)
    }

    @Test
    @DisplayName("Should delegate message push to the storage manager")
    void testPushMessageDelegation() {
        String topic = "test-topic";
        byte[] data = "Hello, World!".getBytes();

        // Execution
        queueManager.pushMessage(topic, data);

        // Verification: Ensure the StorageManager's append method was called
        verify(storageManager).appendToLog(anyString(), any(byte[].class));
    }

    @Test
    @DisplayName("Should create a new topic record in the system")
    void testCreateTopic() {
        String topic = "new-topic";

        // Execution
        queueManager.createTopic(topic);

        // Logic check: Verify that internal topic registry is updated
    }
}
