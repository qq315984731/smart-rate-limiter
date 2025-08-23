-- 幂等性检查并创建记录的Lua脚本
-- KEYS[1]: Redis键
-- ARGV[1]: 执行状态
-- ARGV[2]: 方法签名
-- ARGV[3]: 参数Hash
-- ARGV[4]: 用户ID
-- ARGV[5]: 当前时间
-- ARGV[6]: 过期时间
-- ARGV[7]: 超时秒数

local key = KEYS[1]
local status = ARGV[1]
local methodSignature = ARGV[2]
local parametersHash = ARGV[3]
local userId = ARGV[4]
local currentTime = ARGV[5]
local expireTime = ARGV[6]
local timeoutSeconds = tonumber(ARGV[7])

-- 检查记录是否已存在
local existing = redis.call('GET', key)
if existing then
    return 'EXISTS'
end

-- 创建新的幂等记录
local record = {
    key = key,
    methodSignature = methodSignature,
    parametersHash = parametersHash,
    userId = userId,
    status = status,
    firstRequestTime = currentTime,
    expireTime = expireTime,
    createTime = currentTime,
    lastAccessTime = currentTime,
    accessCount = 1
}

-- 将记录序列化为JSON并存储
local recordJson = cjson.encode(record)
redis.call('SETEX', key, timeoutSeconds, recordJson)

return 'CREATED'