local targets = redis.call('SMEMBERS', KEYS[2])
for _, target in ipairs(targets) do
  redis.call('ZREM', target, ARGV[1])
end
redis.call('DEL', KEYS[1], KEYS[2], KEYS[3])
return 1
