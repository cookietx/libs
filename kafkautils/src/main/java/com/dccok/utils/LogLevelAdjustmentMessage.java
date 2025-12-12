package com.dccok.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LogLevelAdjustmentMessage {
    String applicationName;
    String loggerName;
    String logLevel;
}
