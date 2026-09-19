local mappedAt = redis.call('HGET', KEYS[2], 'projectedAtMillis')
if mappedAt and tonumber(mappedAt) > tonumber(ARGV[4]) then return 0 end
local projectedAt = redis.call('HGET', KEYS[1], 'projectedAtMillis')
if projectedAt and tonumber(projectedAt) > tonumber(ARGV[4]) then return 0 end
redis.call('HSET', KEYS[1],
  'clientId', ARGV[1], 'brokerNode', ARGV[2],
  'connectedAtMillis', ARGV[3], 'projectedAtMillis', ARGV[4])
redis.call('HSET', KEYS[2], 'connectionId', ARGV[5], 'projectedAtMillis', ARGV[4])
redis.call('SADD', KEYS[3], ARGV[5])
redis.call('SADD', KEYS[4], ARGV[1])
return 1
