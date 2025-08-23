/**
 * 管理面板安全功能
 * 为所有API请求添加安全请求头
 */

// 安全配置（将动态加载）
let ADMIN_SECURITY = {
    HEADER_NAME: '',
    HEADER_VALUE: '',
    HEADER_CHECK_ENABLED: false,
    
    // API路径模式（需要安全验证的API）
    SECURED_API_PATTERNS: [
        '/api/config/save',
        '/api/config/delete',
        '/api/endpoints',
        '/api/config/',
        '/api/refresh'
    ]
};

// 标记是否已初始化
let securityInitialized = false;

/**
 * 初始化安全配置
 */
async function initializeSecurity() {
    if (securityInitialized) {
        return;
    }
    
    try {
        const response = await originalFetch(`${window.basePath}/api/security-config`);
        if (response.ok) {
            const result = await response.json();
            if (result.success && result.data) {
                ADMIN_SECURITY.HEADER_CHECK_ENABLED = result.data.headerCheckEnabled || false;
                ADMIN_SECURITY.HEADER_NAME = result.data.headerName || '';
                ADMIN_SECURITY.HEADER_VALUE = result.data.headerValue || '';
                
                console.info('Admin security initialized:', {
                    enabled: ADMIN_SECURITY.HEADER_CHECK_ENABLED,
                    headerName: ADMIN_SECURITY.HEADER_NAME
                });
            }
        }
    } catch (error) {
        console.warn('Failed to initialize security config:', error);
    } finally {
        securityInitialized = true;
    }
}

/**
 * 检查URL是否需要安全验证
 */
function isSecuredApiUrl(url) {
    if (!ADMIN_SECURITY.HEADER_CHECK_ENABLED) {
        return false;
    }
    
    return ADMIN_SECURITY.SECURED_API_PATTERNS.some(pattern => {
        if (pattern.endsWith('/')) {
            return url.includes(pattern);
        }
        return url.includes(pattern);
    });
}

/**
 * 创建安全的fetch请求
 */
async function secureFetch(url, options = {}) {
    // 确保安全配置已初始化
    await initializeSecurity();
    
    // 检查是否需要添加安全头
    if (isSecuredApiUrl(url)) {
        // 确保headers对象存在
        if (!options.headers) {
            options.headers = {};
        }
        
        // 添加安全请求头
        if (ADMIN_SECURITY.HEADER_NAME && ADMIN_SECURITY.HEADER_VALUE) {
            options.headers[ADMIN_SECURITY.HEADER_NAME] = ADMIN_SECURITY.HEADER_VALUE;
            console.debug('Added security header for API request:', url);
        }
    }
    
    return originalFetch(url, options);
}

/**
 * 重写全局fetch以自动添加安全头
 */
(function() {
    const originalFetch = window.fetch;
    
    window.fetch = function(url, options = {}) {
        return secureFetch(url, options);
    };
    
    // 保留原始fetch的引用
    window.originalFetch = originalFetch;
})();

/**
 * 安全的XMLHttpRequest包装
 */
function createSecureXHR() {
    const xhr = new XMLHttpRequest();
    const originalOpen = xhr.open;
    
    xhr.open = function(method, url, async, user, password) {
        // 调用原始的open方法
        originalOpen.call(this, method, url, async, user, password);
        
        // 如果是需要安全验证的API，添加安全头
        if (isSecuredApiUrl(url)) {
            this.setRequestHeader(ADMIN_SECURITY.HEADER_NAME, ADMIN_SECURITY.HEADER_VALUE);
            console.debug('Added security header for XHR request:', url);
        }
    };
    
    return xhr;
}

/**
 * jQuery AJAX 预过滤器（如果使用jQuery）
 */
if (typeof $ !== 'undefined' && $.ajaxPrefilter) {
    $.ajaxPrefilter(function(options, originalOptions, jqXHR) {
        if (isSecuredApiUrl(options.url)) {
            if (!options.headers) {
                options.headers = {};
            }
            options.headers[ADMIN_SECURITY.HEADER_NAME] = ADMIN_SECURITY.HEADER_VALUE;
            console.debug('Added security header for jQuery AJAX request:', options.url);
        }
    });
}

/**
 * 显示安全错误信息
 */
function handleSecurityError(response) {
    if (response.status === 403) {
        response.json().then(data => {
            if (data.error === 'SECURITY_VALIDATION_FAILED') {
                alert('安全验证失败：您没有权限执行此操作。请联系管理员。');
                console.error('Security validation failed:', data);
            }
        }).catch(() => {
            alert('访问被拒绝：安全验证失败');
        });
    }
}

/**
 * 安全的API调用助手函数
 */
const SecureAPI = {
    /**
     * 获取接口列表
     */
    getEndpoints: function(search = '') {
        const url = `${window.basePath}/api/endpoints${search ? '?search=' + encodeURIComponent(search) : ''}`;
        return fetch(url)
            .then(response => {
                if (response.status === 403) {
                    handleSecurityError(response);
                    throw new Error('Security validation failed');
                }
                return response.json();
            });
    },
    
    /**
     * 获取配置
     */
    getConfig: function(methodSignature) {
        const url = `${window.basePath}/api/config/${encodeURIComponent(methodSignature)}`;
        return fetch(url)
            .then(response => {
                if (response.status === 403) {
                    handleSecurityError(response);
                    throw new Error('Security validation failed');
                }
                return response.json();
            });
    },
    
    /**
     * 保存配置
     */
    saveConfig: function(configData) {
        return fetch(`${window.basePath}/api/config/save`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(configData)
        })
        .then(response => {
            if (response.status === 403) {
                handleSecurityError(response);
                throw new Error('Security validation failed');
            }
            return response.json();
        });
    },
    
    /**
     * 删除配置
     */
    deleteConfig: function(methodSignature) {
        const formData = new FormData();
        formData.append('methodSignature', methodSignature);
        
        return fetch(`${window.basePath}/api/config/delete`, {
            method: 'POST',
            body: formData
        })
        .then(response => {
            if (response.status === 403) {
                handleSecurityError(response);
                throw new Error('Security validation failed');
            }
            return response.json();
        });
    },
    
    /**
     * 刷新缓存
     */
    refreshCache: function() {
        return fetch(`${window.basePath}/api/refresh`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(response => {
            if (response.status === 403) {
                handleSecurityError(response);
                throw new Error('Security validation failed');
            }
            return response.json();
        });
    }
};

// 设置全局变量
window.SecureAPI = SecureAPI;
window.ADMIN_SECURITY = ADMIN_SECURITY;
window.initializeSecurity = initializeSecurity;

// 页面加载完成后初始化安全配置
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeSecurity);
} else {
    initializeSecurity();
}

console.info('Admin security module loaded. Security headers will be added automatically when needed.');