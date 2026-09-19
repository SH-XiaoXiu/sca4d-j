local current = redis.call('GET', KEYS[1])
if not current then
    return 0
end
local record = cjson.decode(current)
if record.status ~= 'PROCESSING' or record.token ~= ARGV[1] then
    return 0
end
return redis.call('DEL', KEYS[1])
