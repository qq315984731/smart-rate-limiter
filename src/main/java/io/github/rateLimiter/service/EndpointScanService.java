package io.github.rateLimiter.service;

import io.github.rateLimiter.model.EndpointInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 接口扫描服务
 * 
 * @author RateLimiter Team
 * @since 1.0.0
 */
public interface EndpointScanService {
    
    /**
     * 启动扫描服务
     */
    void startScanning();
    
    /**
     * 停止扫描服务
     */
    void stopScanning();
    
    /**
     * 执行一次完整扫描
     * 
     * @return 扫描结果
     */
    CompletableFuture<List<EndpointInfo>> performScan();
    
    /**
     * 获取扫描状态
     * 
     * @return 扫描状态信息
     */
    ScanStatus getScanStatus();
    
    /**
     * 手动触发扫描
     * 
     * @param force 是否强制扫描（忽略缓存）
     * @return 扫描结果
     */
    CompletableFuture<List<EndpointInfo>> triggerScan(boolean force);
    
    /**
     * 获取扫描历史记录
     * 
     * @param limit 记录数量限制
     * @return 扫描历史
     */
    List<ScanHistory> getScanHistory(int limit);
    
    /**
     * 扫描状态
     */
    class ScanStatus {
        private boolean running;
        private String lastScanTime;
        private String nextScanTime;
        private int totalEndpoints;
        private int configuredEndpoints;
        private int unconfiguredEndpoints;
        private String scanStrategy;
        private long scanDuration;
        private String status;
        
        // Getters and Setters
        public boolean isRunning() { return running; }
        public void setRunning(boolean running) { this.running = running; }
        
        public String getLastScanTime() { return lastScanTime; }
        public void setLastScanTime(String lastScanTime) { this.lastScanTime = lastScanTime; }
        
        public String getNextScanTime() { return nextScanTime; }
        public void setNextScanTime(String nextScanTime) { this.nextScanTime = nextScanTime; }
        
        public int getTotalEndpoints() { return totalEndpoints; }
        public void setTotalEndpoints(int totalEndpoints) { this.totalEndpoints = totalEndpoints; }
        
        public int getConfiguredEndpoints() { return configuredEndpoints; }
        public void setConfiguredEndpoints(int configuredEndpoints) { this.configuredEndpoints = configuredEndpoints; }
        
        public int getUnconfiguredEndpoints() { return unconfiguredEndpoints; }
        public void setUnconfiguredEndpoints(int unconfiguredEndpoints) { this.unconfiguredEndpoints = unconfiguredEndpoints; }
        
        public String getScanStrategy() { return scanStrategy; }
        public void setScanStrategy(String scanStrategy) { this.scanStrategy = scanStrategy; }
        
        public long getScanDuration() { return scanDuration; }
        public void setScanDuration(long scanDuration) { this.scanDuration = scanDuration; }
        
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
    
    /**
     * 扫描历史记录
     */
    class ScanHistory {
        private String scanTime;
        private int endpointsFound;
        private long duration;
        private String strategy;
        private boolean success;
        private String errorMessage;
        
        // Getters and Setters
        public String getScanTime() { return scanTime; }
        public void setScanTime(String scanTime) { this.scanTime = scanTime; }
        
        public int getEndpointsFound() { return endpointsFound; }
        public void setEndpointsFound(int endpointsFound) { this.endpointsFound = endpointsFound; }
        
        public long getDuration() { return duration; }
        public void setDuration(long duration) { this.duration = duration; }
        
        public String getStrategy() { return strategy; }
        public void setStrategy(String strategy) { this.strategy = strategy; }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}