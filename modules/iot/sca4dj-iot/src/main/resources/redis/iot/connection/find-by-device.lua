local index_key = KEYS[1]
local connection_prefix = ARGV[1]
local now = tonumber(ARGV[2])

redis.call('ZREMRANGEBYSCORE', index_key, '-inf', now)
local ids = redis.call('ZRANGEBYSCORE', index_key, now, '+inf')
local result = {}

-- 与 TreeCode.childValue 一致，按 UTF-8 字节编码动态标识。
local function identifier(value)
    return 'V_' .. (string.gsub(value, '.', function(byte)
        return string.format('%02X', string.byte(byte))
    end))
end

for _, id in ipairs(ids) do
    local fields = redis.call(
        'HMGET',
        connection_prefix .. identifier(id),
        'deviceId',
        'protocol',
        'sessionId',
        'accessMethod',
        'connectedAtMillis'
    )
    if fields[1] then
        table.insert(result, id)
        for _, value in ipairs(fields) do
            table.insert(result, value or '')
        end
    else
        redis.call('ZREM', index_key, id)
    end
end

return result
