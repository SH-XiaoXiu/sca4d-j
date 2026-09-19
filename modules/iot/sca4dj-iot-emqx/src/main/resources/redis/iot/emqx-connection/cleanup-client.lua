local connectionId = redis.call('HGET', KEYS[1], 'connectionId')
local projectedAt = redis.call('HGET', KEYS[1], 'projectedAtMillis')
if connectionId == '' and projectedAt and tonumber(projectedAt) <= tonumber(ARGV[2]) then
  redis.call('DEL', KEYS[1])
  redis.call('SREM', KEYS[2], ARGV[1])
  return 1
end
return 0
