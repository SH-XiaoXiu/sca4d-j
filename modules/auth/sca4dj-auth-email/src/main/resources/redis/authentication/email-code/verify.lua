local values = redis.call('HMGET', KEYS[1], 'version', 'status', 'expiresAt', 'attemptCount')
if not values[1] then
    return -2
end
if values[2] ~= 'PENDING' or tonumber(values[3]) <= tonumber(ARGV[4]) then
    return -2
end
if tonumber(values[1]) ~= tonumber(ARGV[1]) then
    return -3
end

local nextVersion = tonumber(values[1]) + 1
if ARGV[2] == '1' then
    redis.call('HSET', KEYS[1], 'status', 'CONSUMED', 'version', nextVersion)
    return nextVersion
end

local attempts = tonumber(values[4]) + 1
local status = 'PENDING'
if attempts >= tonumber(ARGV[3]) then
    status = 'EXHAUSTED'
end
redis.call('HSET', KEYS[1], 'attemptCount', attempts, 'status', status, 'version', nextVersion)
return -1
