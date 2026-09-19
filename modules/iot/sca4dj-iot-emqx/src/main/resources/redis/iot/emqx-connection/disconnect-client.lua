local mappedAt = redis.call('HGET', KEYS[1], 'projectedAtMillis')
if mappedAt and tonumber(mappedAt) > tonumber(ARGV[3]) then return '' end
local connectionId = redis.call('HGET', KEYS[1], 'connectionId')
redis.call('HSET', KEYS[1], 'connectionId', '', 'projectedAtMillis', ARGV[3])
redis.call('SADD', KEYS[3], ARGV[2])
if not connectionId or connectionId == '' then return '' end
redis.call('DEL', ARGV[1] .. connectionId)
redis.call('SREM', KEYS[2], connectionId)
return connectionId
