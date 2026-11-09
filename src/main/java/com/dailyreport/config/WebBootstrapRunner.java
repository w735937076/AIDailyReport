package com.dailyreport.config;

import com.dailyreport.service.SettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

@Slf4j
@Component
@Order(100)
public class WebBootstrapRunner implements ApplicationRunner {

    private final SettingsService settingsService;

    @Value("${server.port:18080}")
    private int serverPort;

    @Value("${app.open-browser:true}")
    private boolean openBrowserDefault;

    public WebBootstrapRunner(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        settingsService.bootstrapFromYamlIfEmpty();
        boolean open = openBrowserDefault && settingsService.isOpenBrowserOnStart();
        if (!open) {
            return;
        }
        String url = "http://localhost:" + serverPort + "/";
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("Opened browser at {}", url);
            } else {
                log.info("Web UI ready at {} (open manually in your browser)", url);
            }
        } catch (Exception e) {
            log.info("Web UI ready at {} — could not auto-open browser: {}", url, e.getMessage());
        }
    }
}
