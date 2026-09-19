local clientId = redis.call('HGET', KEYS[1], 'clientId')
if clientId then
  local clientKey = ARGV[1] .. clientId
  if redis.call('HGET', clientKey, 'connectionId') == ARGV[2] then
    redis.call('DEL', clientKey)
    redis.call('SREM', KEYS[3], clientId)
  end
end
redis.call('DEL', KEYS[1])
redis.call('SREM', KEYS[2], ARGV[2])
return clientId and 1 or 0
