-- Finite 15-round magazine. Successful hits are refunded on the server.
local M = {}
local CAPACITY = 15
local FEED_TIME = 2250
local FINISH_TIME = 2333.3333333333
function M.shoot(api)
    if api:getAmmoCountInMagazine() <= 0 then return end
    api:shootOnce(true)
end
function M.start_reload(api)
    local count=api:getAmmoCountInMagazine()
    if count >= CAPACITY or not api:hasAmmoToConsume() then return false end
    api:cacheScriptData({judgfire_reload=true, loaded=false, empty=count<=0})
    return true
end
function M.tick_reload(api)
    local cache=api:getCachedScriptData()
    if cache==nil or not cache.judgfire_reload then return NOT_RELOADING,-1 end
    local time=api:getReloadTime()
    local feeding=cache.empty and EMPTY_RELOAD_FEEDING or TACTICAL_RELOAD_FEEDING
    local finishing=cache.empty and EMPTY_RELOAD_FINISHING or TACTICAL_RELOAD_FINISHING
    if time<FEED_TIME then return feeding,FEED_TIME-time end
    if not cache.loaded then
        -- Automatic hits may have restored rounds during the reload.
        local needed=math.max(0,CAPACITY-api:getAmmoCountInMagazine())
        local paid=needed
        if needed>0 and api:isReloadingNeedConsumeAmmo() then
            paid=api:consumeAmmoFromPlayer(needed)
        end
        if paid>0 then api:putAmmoInMagazine(math.min(needed,paid)) end
        api:setAmmoInBarrel(false)
        cache.loaded=true
        api:cacheScriptData(cache)
    end
    if time<FINISH_TIME then return finishing,FINISH_TIME-time end
    return NOT_RELOADING,-1
end
return M
