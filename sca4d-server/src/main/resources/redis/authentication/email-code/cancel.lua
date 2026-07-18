if redis.call('get', KEYS[2]) == KEYS[1] then
  redis.call('del', KEYS[2])
  redis.call('del', KEYS[3])
end
return redis.call('del', KEYS[1])
