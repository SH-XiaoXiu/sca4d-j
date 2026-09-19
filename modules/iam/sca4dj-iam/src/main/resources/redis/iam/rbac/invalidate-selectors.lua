for keyIndex = 1, #KEYS, 2 do
    redis.call('INCR', KEYS[keyIndex])
    redis.call('DEL', KEYS[keyIndex + 1])
end
return #KEYS / 2
