if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
local targets = redis.call('SMEMBERS', KEYS[2])
for _, target in ipairs(targets) do
  redis.call('ZADD', target, ARGV[2], ARGV[1])
  redis.call('PEXPIRE', target, ARGV[3] * 2)
end
redis.call('PEXPIRE', KEYS[1], ARGV[3])
redis.call('PEXPIRE', KEYS[2], ARGV[3])
redis.call('PEXPIRE', KEYS[3], ARGV[3])
return 1
