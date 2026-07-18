local current = redis.call('get', KEYS[1])
if not current then
  return 0
end

local ok, enrollment = pcall(cjson.decode, current)
if not ok
    or tonumber(enrollment['version']) ~= tonumber(ARGV[1])
    or enrollment['state'] ~= ARGV[2] then
  return 0
end

redis.call('set', KEYS[1], ARGV[3], 'KEEPTTL')
return 1
