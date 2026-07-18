if redis.call('exists', KEYS[3]) == 1 then
  return 0
end

local previous = redis.call('get', KEYS[2])
if previous then
  redis.call('del', previous)
end

redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2])
redis.call('set', KEYS[2], KEYS[1], 'PX', ARGV[2])
redis.call('set', KEYS[3], '1', 'PX', ARGV[3])
return 1
