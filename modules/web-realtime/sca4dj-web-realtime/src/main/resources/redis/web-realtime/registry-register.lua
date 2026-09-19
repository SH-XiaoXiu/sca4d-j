redis.call('HSET', KEYS[1], 'node', ARGV[2], 'session', ARGV[3], 'account', ARGV[4], 'subject', ARGV[5])
redis.call('PEXPIRE', KEYS[1], ARGV[7])
redis.call('DEL', KEYS[2], KEYS[3])
for i = 4, #KEYS do
  redis.call('SADD', KEYS[2], KEYS[i])
  redis.call('ZADD', KEYS[i], ARGV[6], ARGV[1])
  redis.call('PEXPIRE', KEYS[i], ARGV[7] * 2)
end
redis.call('PEXPIRE', KEYS[2], ARGV[7])
redis.call('PEXPIRE', KEYS[3], ARGV[7])
return 1
