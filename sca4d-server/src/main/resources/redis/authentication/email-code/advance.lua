local current = redis.call('get', KEYS[1])
if not current or redis.call('get', KEYS[2]) ~= KEYS[1] then
  return 0
end

local ok, challenge = pcall(cjson.decode, current)
if not ok
    or tonumber(challenge['version']) ~= tonumber(ARGV[1])
    or challenge['state'] ~= ARGV[2] then
  return 0
end

redis.call('set', KEYS[1], ARGV[3], 'KEEPTTL')
if ARGV[4] == '1' then
  redis.call('del', KEYS[2])
end
return 1
