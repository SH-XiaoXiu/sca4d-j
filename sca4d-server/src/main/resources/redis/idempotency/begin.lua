local acquired = redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX')
if acquired then
  return ARGV[3]
end
return redis.call('get', KEYS[1])
