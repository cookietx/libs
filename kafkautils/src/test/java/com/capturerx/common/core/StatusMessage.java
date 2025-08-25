package com.capturerx.common.core;

import lombok.Data;

import java.util.UUID;

@Data
public class StatusMessage {
    private UUID corRelatedId;
    private String sender;
    private String transportSettingsId;
    private String fileName;
    private String fileHash;
    private String fullFilePath;
    private int fileTypeId;
    private String statusCode;
    private String statusDetails;
}
