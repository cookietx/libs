package com.dccok.utils;

import lombok.Data;

import java.util.UUID;

@Data
public class WorkerMessage {
    private UUID id = UUID.randomUUID();
    private String sender;
    private String transportSettingsId;
    private String fileName;
    private String fileHash;
    private String fullFilePath;
    private int fileTypeId;
}
