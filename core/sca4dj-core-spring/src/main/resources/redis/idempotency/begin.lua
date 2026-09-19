local current = redis.call('GET', KEYS[1])
if current then
    return current
end
redis.call('PSETEX', KEYS[1], ARGV[2], ARGV[1])
return ARGV[3]
