package com.bandclub.rehearsal.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(
        String clubName,
        String initialAdminLoginId,
        String initialAdminPassword,
        String initialAdminName
) {
}
