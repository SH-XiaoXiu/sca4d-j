local current = redis.call('get', KEYS[1])
if not current then
  return 0
end

local ok, registration = pcall(cjson.decode, current)
if not ok
    or tonumber(registration['version']) ~= tonumber(ARGV[1])
    or registration['state'] ~= ARGV[2] then
  return 0
end

redis.call('set', KEYS[1], ARGV[3], 'KEEPTTL')
return 1
