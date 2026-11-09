package com.dailyreport.web;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String path) {
        File file = new File(path);
        if (!file.isFile() || !file.exists()) {
            return ResponseEntity.notFound().build();
        }
        String normalized = file.getAbsoluteFile().toPath().normalize().toString();
        if (!normalized.startsWith(new File("./output").getAbsoluteFile().toPath().normalize().toString())
                && !normalized.contains(File.separator + "output" + File.separator)) {
            return ResponseEntity.badRequest().build();
        }
        String filename = file.getName();
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(file));
    }
}
