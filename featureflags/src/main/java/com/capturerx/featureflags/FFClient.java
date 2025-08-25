package com.capturerx.featureflags;

import org.springframework.stereotype.Component;

@Component
public interface FFClient {

    public String prefixAppName(String key);
    String getStringVariation(String key, String dflt);
    Integer getIntegerVariation(String key, Integer dflt);
    Double getDoubleVariation(String key, Double dflt);
    Boolean getBooleanVariation(String key, Boolean dflt);
}
