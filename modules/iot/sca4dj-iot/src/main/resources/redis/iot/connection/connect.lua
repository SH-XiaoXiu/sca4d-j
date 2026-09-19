local changed = redis.call('HGET', KEYS[1], 'deviceId') ~= ARGV[1]
  or redis.call('HGET', KEYS[1], 'protocol') ~= ARGV[2]
  or redis.call('HGET', KEYS[1], 'sessionId') ~= ARGV[3]
  or redis.call('HGET', KEYS[1], 'accessMethod') ~= ARGV[4]
  or redis.call('HGET', KEYS[1], 'connectedAtMillis') ~= ARGV[5]
redis.call('HSET', KEYS[1],
  'deviceId', ARGV[1], 'protocol', ARGV[2], 'sessionId', ARGV[3],
  'accessMethod', ARGV[4], 'connectedAtMillis', ARGV[5])
redis.call('PEXPIRE', KEYS[1], ARGV[6])
redis.call('ZADD', KEYS[2], ARGV[7], ARGV[8])
redis.call('PEXPIRE', KEYS[2], ARGV[6])
redis.call('ZADD', KEYS[3], ARGV[7], ARGV[8])
redis.call('PEXPIRE', KEYS[3], ARGV[6])
if changed then return tonumber(ARGV[1]) end
return 0
