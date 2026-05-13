-- 固定窗口限流 Lua 脚本
-- KEYS[1]: 限流 key（如 rate_limit:{keyHash}:m:{minute}）
-- ARGV[1]: 窗口内最大请求数
-- ARGV[2]: 窗口大小（秒）
-- 返回: {当前计数, TTL}

local current = redis.call('INCR', KEYS[1])
if current == 1 then
    redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
end
local ttl = redis.call('TTL', KEYS[1])
return {current, ttl}
