local target = redis.call('HGET', KEYS[2], ARGV[2])
if not target then
  return 0
end
redis.call('HDEL', KEYS[2], ARGV[2])
local remaining = redis.call('HVALS', KEYS[2])
for _, value in ipairs(remaining) do
  if value == target then
    return 1
  end
end
redis.call('SREM', KEYS[1], target)
redis.call('ZREM', target, ARGV[1])
return 1
