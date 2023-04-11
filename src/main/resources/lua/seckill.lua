-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]

-- 优惠券库存key
local stockKey = 'seckill:stock:' .. voucherId
-- 订单key
local orderKey = 'seckill:order:' .. voucherId

if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 优惠券库存不足
    return 1
end

if (redis.call('sismember', orderKey, userId) == 1) then
    -- 优惠券已被该用户购买
    return 2
end

-- 扣减优惠券库存
redis.call('decr', stockKey)
-- 订单中添加当前用户
redis.call('sadd', orderKey, userId)

return 0