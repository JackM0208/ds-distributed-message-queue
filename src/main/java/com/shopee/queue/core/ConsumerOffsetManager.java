package com.shopee.queue.core;

import java.io.*;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConsumerOffsetManager {
    private final ConcurrentHashMap<String, Long> offsetMap = new ConcurrentHashMap<>();
    private final File offsetFile = new File("data/offsets.properties");

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public ConsumerOffsetManager() {
        loadFromDisk();

        scheduler.scheduleAtFixedRate(this::saveToDisk, 5, 5, TimeUnit.SECONDS);

    }

    public void commitOffset(String consumerId, String topicName, long offset) {
        String key = consumerId + "-" + topicName;
        offsetMap.put(key, offset);
        //saveToDisk(); In production, do this asynchronously in a background thread
    }

    public long getOffset(String consumerId, String topicName) {
        String key = consumerId + "-" + topicName;
        return offsetMap.getOrDefault(key, 0L);
    }

    private synchronized void saveToDisk() {
        Properties props = new Properties();
        offsetMap.forEach((k, v) -> props.setProperty(k, String.valueOf(v)));
        try (FileOutputStream out = new FileOutputStream(offsetFile)) {
            props.store(out, "Consumer Offsets");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadFromDisk() {
        if (!offsetFile.exists()) return;
        try (FileInputStream in = new FileInputStream(offsetFile)) {
            Properties props = new Properties();
            props.load(in);
            for (String key : props.stringPropertyNames()) {
                offsetMap.put(key, Long.parseLong(props.getProperty(key)));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}