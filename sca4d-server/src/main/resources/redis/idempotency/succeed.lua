local current = redis.call('get', KEYS[1])
if not current then
  return 0
end
local ok, record = pcall(cjson.decode, current)
if not ok or record['token'] ~= ARGV[1] then
  return 0
end
redis.call('psetex', KEYS[1], ARGV[3], ARGV[2])
return 1
