if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
if redis.call('HSETNX', KEYS[4], ARGV[4], KEYS[3]) == 0 then
  return -1
end
redis.call('SADD', KEYS[2], KEYS[3])
redis.call('ZADD', KEYS[3], ARGV[2], ARGV[1])
redis.call('PEXPIRE', KEYS[3], ARGV[3] * 2)
redis.call('PEXPIRE', KEYS[2], ARGV[3])
redis.call('PEXPIRE', KEYS[4], ARGV[3])
return 1
