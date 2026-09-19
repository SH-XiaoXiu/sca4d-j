local index_key = KEYS[1]
local connection_prefix = ARGV[1]
local now = tonumber(ARGV[2])

redis.call('ZREMRANGEBYSCORE', index_key, '-inf', now)
local ids = redis.call('ZRANGEBYSCORE', index_key, now, '+inf')
local result = {}

for _, id in ipairs(ids) do
    local fields = redis.call(
        'HMGET',
        connection_prefix .. id,
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
