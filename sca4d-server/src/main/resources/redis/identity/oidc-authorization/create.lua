local created = redis.call('set', KEYS[1], ARGV[1], 'PX', ARGV[2], 'NX')
if created then
  return 1
end
return 0
