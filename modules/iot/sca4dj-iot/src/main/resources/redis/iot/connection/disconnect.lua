if redis.call('HGET', KEYS[1], 'protocol') ~= ARGV[1] then return 0 end
local deviceId = redis.call('HGET', KEYS[1], 'deviceId')
if not deviceId then return 0 end
redis.call('DEL', KEYS[1])
redis.call('ZREM', ARGV[2] .. deviceId, ARGV[3])
redis.call('ZREM', KEYS[2], ARGV[3])
return tonumber(deviceId)
