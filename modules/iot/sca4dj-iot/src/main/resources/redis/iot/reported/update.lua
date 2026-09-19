local group_code = ARGV[1]
local order_scope = ARGV[2]
local order_value = ARGV[3]
local merge_mode = ARGV[4]
local observed_at = ARGV[5]
local received_at = ARGV[6]
local covered_count = tonumber(ARGV[7])
local offset = 8
local covered = {}

for index = 1, covered_count do
    covered[ARGV[offset]] = true
    offset = offset + 1
end

local value_count = tonumber(ARGV[offset])
offset = offset + 1
local values = {}

for index = 1, value_count do
    values[ARGV[offset]] = ARGV[offset + 1]
    offset = offset + 2
end

local current_scope = redis.call('HGET', KEYS[1], 'orderScope')
if current_scope then
    if current_scope ~= order_scope then
        return 'CONFLICT_SCOPE'
    end

    local current_order = redis.call('HGET', KEYS[1], 'orderValue')
    local version = redis.call('HGET', KEYS[1], 'version')
    if order_value < current_order then
        return 'OLDER:' .. version
    end

    if order_value == current_order then
        for code, value in pairs(values) do
            if redis.call('HGET', KEYS[1], 'value:' .. code) ~= value then
                return 'CONFLICT_CONTENT'
            end
        end
        if merge_mode == 'SNAPSHOT' then
            for code, _ in pairs(covered) do
                if values[code] == nil and redis.call('HEXISTS', KEYS[1], 'value:' .. code) == 1 then
                    return 'CONFLICT_CONTENT'
                end
            end
        end
        return 'DUPLICATE:' .. version
    end
end

if merge_mode == 'SNAPSHOT' then
    for code, _ in pairs(covered) do
        if values[code] == nil then
            redis.call('HDEL', KEYS[1], 'value:' .. code)
        end
    end
end

for code, value in pairs(values) do
    redis.call('HSET', KEYS[1], 'value:' .. code, value)
end

local version = 1
if current_scope then
    version = tonumber(redis.call('HGET', KEYS[1], 'version')) + 1
end
redis.call('HSET', KEYS[1],
    'orderScope', order_scope,
    'orderValue', order_value,
    'version', version,
    'observedAtMillis', observed_at,
    'receivedAtMillis', received_at)
redis.call('SADD', KEYS[2], group_code)
return 'ACCEPTED:' .. version
