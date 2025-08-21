package io.github.rateLimiter.service.impl;

import io.github.rateLimiter.config.RateLimiterAdminProperties;
import io.github.rateLimiter.model.DynamicRateLimitConfig;
import io.github.rateLimiter.service.FileLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文件日志服务实现 - 简化版本
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
@Service
public class FileLogServiceImpl implements FileLogService {
    
    private static final Logger log = LoggerFactory.getLogger(FileLogServiceImpl.class);
    
    private final RateLimiterAdminProperties adminProperties;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public FileLogServiceImpl(RateLimiterAdminProperties adminProperties) {
        this.adminProperties = adminProperties;
        // 确保日志目录存在
        ensureLogDirectoryExists();
    }
    
    @Override
    public void logConfigChange(String methodSignature, 
                               DynamicRateLimitConfig beforeConfig, 
                               DynamicRateLimitConfig afterConfig, 
                               String operator) {
        if (!adminProperties.getLogging().isFileEnabled()) {
            return;
        }
        
        try {
            String beforeText = beforeConfig == null ? "无限流" : formatAsAnnotation(beforeConfig);
            String afterText = afterConfig == null ? "无限流" : formatAsAnnotation(afterConfig);
            
            String logMessage = String.format("%s | %s => %s | %s",
                methodSignature, beforeText, afterText, operator);
            
            // 直接写入配置的文件路径，不依赖Logger配置
            Path logPath = Paths.get(adminProperties.getLogging().getFilePath());
            String fileEntry = String.format("%s | %s%n", LocalDateTime.now().format(formatter), logMessage);
            Files.write(logPath, fileEntry.getBytes(), 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                
            log.debug("记录配置变更: {}", logMessage);
        } catch (IOException e) {
            log.error("写入日志文件失败: {}", e.getMessage());
        }
    }
    
    private void ensureLogDirectoryExists() {
        try {
            Path logPath = Paths.get(adminProperties.getLogging().getFilePath());
            Path logDir = logPath.getParent();
            if (logDir != null && !Files.exists(logDir)) {
                Files.createDirectories(logDir);
                log.info("创建日志目录: {}", logDir);
            }
        } catch (IOException e) {
            log.error("创建日志目录失败: {}", e.getMessage());
        }
    }
    
    private String formatAsAnnotation(DynamicRateLimitConfig config) {
        StringBuilder sb = new StringBuilder("@RateLimit(");
        sb.append("permits=").append(config.getPermits());
        sb.append(", window=").append(config.getWindow());
        
        if (config.getDimension() != null && !config.getDimension().isEmpty()) {
            sb.append(", dimension=").append(config.getDimension());
        }
        
        if (config.getAlgorithm() != null && !config.getAlgorithm().isEmpty()) {
            sb.append(", algorithm=").append(config.getAlgorithm());
        }
        
        if (config.getMessage() != null && !config.getMessage().isEmpty()) {
            sb.append(", message=\"").append(config.getMessage()).append("\"");
        }
        
        sb.append(")");
        return sb.toString();
    }
}