-- Per-gun adaptation of TaCZ's official default state machine.
-- Clone required tables: a cached default module is shared with other guns.
local default = require("tacz_default_state_machine")

local function clone(value, seen)
    if type(value) ~= "table" then return value end
    seen = seen or {}
    if seen[value] then return seen[value] end
    local result = {}
    seen[value] = result
    for key, item in pairs(value) do
        result[clone(key, seen)] = clone(item, seen)
    end
    return setmetatable(result, getmetatable(value))
end

local M = clone(default)

function M.gun_kick_state.transition(this, context, input)
    if input == INPUT_SHOOT then
        -- The 1.3 s raw action can be interrupted by the next 150 RPM shot.
        -- Native runAnimation with zero transition replaces this track's
        -- runner at progress zero; idle, reload, inspect and other guns stay intact.
        local track = context:getAsSingletonTrack(this.GUN_KICK_TRACK_LINE)
        context:runAnimation("shoot", track, true, PLAY_ONCE_STOP, 0)
    end
    return nil
end


-- Separate visual input: never calls the gameplay reload or changes ammunition.
local original_idle = M.main_track_states.idle.transition
function M.main_track_states.idle.transition(this, context, input)
    if input == "parasite_cannon_toggle_ultimate" then
        context:stopAnimation(context:getAsSingletonTrack(this.GUN_KICK_TRACK_LINE))
        context:runAnimation("parasite_cannon_toggle_ultimate", context:getTrack(this.STATIC_TRACK_LINE, this.MAIN_TRACK), false, PLAY_ONCE_STOP, 0.1)
        return this.main_track_states.idle
    end
    if input == "parasite_cannon_toggle" then
        context:stopAnimation(context:getAsSingletonTrack(this.GUN_KICK_TRACK_LINE))
        context:runAnimation("reload_tactical", context:getTrack(this.STATIC_TRACK_LINE, this.MAIN_TRACK), false, PLAY_ONCE_STOP, 0.2)
        return this.main_track_states.idle
    end
    return original_idle(this, context, input)
end

return M
