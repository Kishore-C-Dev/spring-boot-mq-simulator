package com.mqsim.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NamespaceConfig {

    @Value("${mqsim.namespace:default}")
    private String namespace;

    public String getNamespace() {
        return namespace;
    }
}
