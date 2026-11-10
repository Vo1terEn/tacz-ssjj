-- Copy the native state machine's tables so the override never changes another gun.
local function copy(value,seen)
    if type(value)~="table" then return value end
    seen=seen or {}
    if seen[value] then return seen[value] end
    local result={};seen[value]=result
    for k,v in pairs(value) do result[copy(k,seen)]=copy(v,seen) end
    return setmetatable(result,getmetatable(value))
end
local M=copy(require("tacz_default_state_machine"))
local original=M.gun_kick_state.transition
function M.gun_kick_state.transition(this,context,input)
    if context:getAttachment("MUZZLE")=="judgfire:dragon_breath_muzzle" then
        -- No camera recoil or mesh kick on the laser attachment.
        return nil
    end
    return original(this,context,input)
end
-- The native rifle transition blends the reload in over 0.2 seconds.  The
-- source clip already contains its full anticipation, so start it at full
-- weight to keep the original 30-fps timing and source_anim_clock intact.
local originalMainTransition=M.main_track_states.idle.transition
function M.main_track_states.idle.transition(this,context,input)
    if input==INPUT_RELOAD then
        if context:getAmmoCount()>=15 then return nil end
        local empty=(not context:hasBulletInBarrel()) and context:getAmmoCount()<=0
        local name=empty and "reload_empty" or "reload_tactical"
        context:runAnimation(name,context:getTrack(this.STATIC_TRACK_LINE,this.MAIN_TRACK),false,PLAY_ONCE_STOP,0)
        return this.main_track_states.idle
    end
    return originalMainTransition(this,context,input)
end
return M
