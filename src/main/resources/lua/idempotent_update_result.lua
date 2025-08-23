-- 幂等性更新执行结果的Lua脚本
-- KEYS[1]: Redis键
-- ARGV[1]: 新的执行状态 (SUCCESS/FAILED)
-- ARGV[2]: 结果数据或错误信息
-- ARGV[3]: 当前时间

local key = KEYS[1]
local newStatus = ARGV[1]
local resultData = ARGV[2]
local currentTime = ARGV[3]

-- 获取现有记录
local existing = redis.call('GET', key)
if not existing then
    return false
end

-- 解析现有记录
local record = cjson.decode(existing)

-- 更新记录状态和结果
record.status = newStatus
record.lastAccessTime = currentTime

-- 增加访问计数
if record.accessCount then
    record.accessCount = record.accessCount + 1
else
    record.accessCount = 1
end

-- 根据状态设置结果或错误信息
if newStatus == 'SUCCESS' then
    record.result = resultData
    record.errorMessage = nil
elseif newStatus == 'FAILED' then
    record.errorMessage = resultData
    record.result = nil
end

-- 获取原有的TTL并保持
local ttl = redis.call('TTL', key)
if ttl > 0 then
    -- 将更新后的记录写回Redis，保持原有TTL
    local recordJson = cjson.encode(record)
    redis.call('SETEX', key, ttl, recordJson)
else
    -- 如果没有TTL或已过期，使用默认的300秒
    local recordJson = cjson.encode(record)
    redis.call('SETEX', key, 300, recordJson)
end

return true