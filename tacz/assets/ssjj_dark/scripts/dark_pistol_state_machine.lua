-- Independent TaCZ pistol adapter. Blade damage stays on the server.
-- A script/factory may be shared by several native display instances. Keep
-- operation gates on each actual Java context, never on the shared script self.
local inspect_blocked = {}
local pose = {}
function pose.entry(self, context)
    context:runAnimation("static_idle", context:getTrack(0, 0), false, LOOP, 0)
end
function pose.transition(self, context, input)
    if input == "dark_inspect_blocked" then inspect_blocked[context] = true
    elseif input == "dark_inspect_ready" then inspect_blocked[context] = false
    elseif input == INPUT_INSPECT and not inspect_blocked[context] then
        context:runAnimation("inspect", context:getTrack(0, 1), false, PLAY_ONCE_STOP, 0.08)
    end
end
local machine = {}
function machine:initialize(context)
    inspect_blocked[context] = false
    context:ensureTrackLineSize(1)
    context:ensureTracksAmount(0, 8)
    context:runAnimation("draw", context:getTrack(0, 1), false, PLAY_ONCE_STOP, 0.05)
end
function machine:states() return {pose} end
function machine:exit(context)
    inspect_blocked[context] = nil
    for i = 0, 7 do context:stopAnimation(context:getTrack(0, i)) end
end
return machine
