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
        -- The .5 s source action is restarted by each 300 ms shot.
        -- Native runAnimation with zero transition replaces this track's
        -- runner at progress zero; idle, reload, inspect and other guns stay intact.
        local track = context:getAsSingletonTrack(this.GUN_KICK_TRACK_LINE)
        context:runAnimation("shoot", track, true, PLAY_ONCE_STOP, 0)
    end
    return nil
end


local idle_transition = M.main_track_states.idle.transition
M.main_track_states.dimension_field = {}
function M.main_track_states.idle.transition(this, context, input)
    if input == "dimension_field_start" then
        context:stopAnimation(context:getAsSingletonTrack(this.GUN_KICK_TRACK_LINE))
        context:runAnimation("dimension_field_start", context:getTrack(this.STATIC_TRACK_LINE, this.MAIN_TRACK), false, PLAY_ONCE_STOP, 0)
        return this.main_track_states.dimension_field
    end
    return idle_transition(this, context, input)
end
function M.main_track_states.dimension_field.update(this, context)
    if context:isStopped(context:getTrack(this.STATIC_TRACK_LINE, this.MAIN_TRACK)) then
        context:trigger("dimension_field_end")
    end
end
function M.main_track_states.dimension_field.transition(this, context, input)
    if input == "dimension_field_end" then
        context:stopAnimation(context:getTrack(this.STATIC_TRACK_LINE, this.MAIN_TRACK))
        return this.main_track_states.idle
    end
    if input == INPUT_SHOOT or input == INPUT_RELOAD or input == INPUT_PUT_AWAY        or input == INPUT_BOLT or input == INPUT_INSPECT or input == INPUT_RUN        or input == INPUT_BAYONET_MUZZLE or input == INPUT_BAYONET_STOCK or input == INPUT_BAYONET_PUSH then        context:stopAnimation(context:getTrack(this.STATIC_TRACK_LINE, this.MAIN_TRACK))
        return idle_transition(this, context, input) or this.main_track_states.idle    end
    return nil
end
return M
