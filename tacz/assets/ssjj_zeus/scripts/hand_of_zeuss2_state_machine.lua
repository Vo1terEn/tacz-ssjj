-- TaCZ's official states supply draw, reload, inspect, ADS and locomotion.
-- Deep-copy the cached module to keep every change local to Zeus Hand II.
local default = require("tacz_default_state_machine")
local function clone(value, seen)
    if type(value) ~= "table" then return value end
    seen = seen or {}
    if seen[value] then return seen[value] end
    local result = {}
    seen[value] = result
    for key, item in pairs(value) do result[clone(key, seen)] = clone(item, seen) end
    return setmetatable(result, getmetatable(value))
end
local M = clone(default)

-- The source supplies no dedicated ADS start/end actions. TaCZ's iron sight
-- positioning still aims the gun; do not request a fallback rifle animation.
function M.ADS_states.normal.update(this, context)
end

-- The available Zeus source has no physical casing mesh/ejection action.
local default_main_transition = M.main_track_states.idle.transition
function M.main_track_states.idle.transition(this, context, input)
    if input == INPUT_SHOOT then return this.main_track_states.idle end
    return default_main_transition(this, context, input)
end

function M.gun_kick_state.transition(this, context, input)
    if input == INPUT_SHOOT then
        -- Restart the source recoil clip, without accumulating previous shots.
        -- TaCZ GunData remains the single source of the gameplay shot interval.
        local track = context:getAsSingletonTrack(this.GUN_KICK_TRACK_LINE)
        context:runAnimation("shoot", track, true, PLAY_ONCE_STOP, 0)
    end
    return nil
end

-- Zeus Advent uses a supplied source clip on an existing native parallel
-- static track. The ordinary reload/shoot/locomotion state is never replaced.
M.zeus_advent_state = {}
function M.zeus_advent_state.transition(this, context, input)
    local track = context:getTrack(this.STATIC_TRACK_LINE, this.PARALLEL_TRACK_1)
    if input == "zeus_advent" then
        if context:isStopped(track) then
            context:runAnimation("zeus_advent", track, false, PLAY_ONCE_STOP, 0.12)
        end
    elseif input == INPUT_PUT_AWAY then
        context:stopAnimation(track)
    end
    return nil
end
local zeus_original_states = M.states
function M:states()
    local states = zeus_original_states(self)
    table.insert(states, self.zeus_advent_state)
    return states
end

return M
