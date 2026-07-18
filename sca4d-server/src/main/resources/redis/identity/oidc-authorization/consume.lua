local current = redis.call('get', KEYS[1])
if not current then
  return nil
end
local ok, authorization = pcall(cjson.decode, current)
if not ok or authorization['browserHash'] ~= ARGV[1] then
  return nil
end
redis.call('del', KEYS[1])
return current
