local written = 0
for keyIndex = 1, #KEYS, 2 do
    local coordinateIndex = (keyIndex + 1) / 2
    local argumentIndex = (coordinateIndex - 1) * 3 + 1
    local currentRevision = redis.call('GET', KEYS[keyIndex]) or '0'
    if currentRevision == ARGV[argumentIndex] then
        redis.call('SET', KEYS[keyIndex + 1], ARGV[argumentIndex + 1], 'PX', ARGV[argumentIndex + 2])
        written = written + 1
    end
end
return written
