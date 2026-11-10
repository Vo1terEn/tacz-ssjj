-- Keep all native gun, hand, reload, inspect, ADS, recoil and movement states.
local default = require("tacz_default_state_machine")
local spin_state = {}
local M = setmetatable({}, {__index = default})

-- BLEND_TRACK_5 is reserved for extensions in TaCZ's default state machine.
-- Its additive clip contains only the three muzzle-part rotation channels.
local function spinTrack(context)
    return context:getTrack(default.BLENDING_TRACK_LINE, default.BLEND_TRACK_5)
end

function M:initialize(context)
    default.initialize(self, context)
    self.eye_spin_phase = 0
    self.eye_spin_speed = 0
    self.eye_spin_time = context:getCurrentTimestamp()
    self.eye_spin_return = nil
    self.eye_spin_hold_until = 0
    local track = spinTrack(context)
    context:runAnimation("eye_muzzle_rotation", track, true, LOOP, 0)
    context:pauseAnimation(track)
    context:setAnimationProgress(track, 0, true)
end

local function beginReturn(this)
    local distance = 1 - this.eye_spin_phase
    -- Keep enough space for a smooth stop. Near the home angle, complete one
    -- extra forward revolution instead of braking abruptly or reversing.
    if distance < this.eye_spin_speed * 0.2 / 1.5 then
        distance = distance + 1
    end
    this.eye_spin_return = {
        phase = this.eye_spin_phase,
        speed = this.eye_spin_speed,
        distance = distance,
        duration = math.max(0.2, 2.5 * distance),
        elapsed = 0
    }
end

-- Reset then hold the muzzle at its home angle for the entire native 0.5-second shoot clip.
-- Repeated shots extend the hold; the ordinary gun-kick state still runs.
function spin_state.transition(this, context, input)
    if input == INPUT_SHOOT then
        this.eye_spin_hold_until = context:getCurrentTimestamp() + 500
        this.eye_spin_speed = 0
        this.eye_spin_phase = 0
        this.eye_spin_time = context:getCurrentTimestamp()
        context:setAnimationProgress(spinTrack(context), 0, true)
        this.eye_spin_return = nil
    end
    return nil
end

function spin_state.update(this, context)
    local now = context:getCurrentTimestamp()
    local dt = math.max(0, math.min((now - this.eye_spin_time) / 1000, 0.1))
    this.eye_spin_time = now
    if now < this.eye_spin_hold_until then
        -- Keep the reset home pose throughout the firing hold.
        return
    end
    local moving = (context:isInputUp() ~= context:isInputDown()) or
                   (context:isInputLeft() ~= context:isInputRight())
    if moving then
        -- Resume from the exact current phase AND speed if returning was interrupted.
        this.eye_spin_return = nil
        local before = this.eye_spin_speed
        local step = 3 * dt
        if before < 0.6 then
            this.eye_spin_speed = math.min(0.6, before + step)
        else
            this.eye_spin_speed = math.max(0.6, before - step)
        end
        this.eye_spin_phase = (this.eye_spin_phase +
                              (before + this.eye_spin_speed) * 0.5 * dt) % 1
    else
        if this.eye_spin_return == nil and
           (this.eye_spin_phase > 0.000000001 or this.eye_spin_speed > 0.000000001) then
            beginReturn(this)
        end
        local returning = this.eye_spin_return
        if returning ~= nil then
            returning.elapsed = math.min(returning.duration, returning.elapsed + dt)
            local u = returning.elapsed / returning.duration
            if u >= 1 then
                this.eye_spin_phase = 0
                this.eye_spin_speed = 0
                this.eye_spin_return = nil
            else
                -- Cubic Hermite curve: current speed at entry, zero speed at home.
                -- 0 <= speed*duration/distance <= 1.5, so the curve stays forward.
                local d, t, v = returning.distance, returning.duration, returning.speed
                local advance = d * (3*u*u - 2*u*u*u) + v*t * (u*u*u - 2*u*u + u)
                this.eye_spin_phase = (returning.phase + advance) % 1
                this.eye_spin_speed = (d * (6*u - 6*u*u) +
                                      v*t * (3*u*u - 4*u + 1)) / t
            end
        end
    end
    -- No stop/fade/restart: reaching the next complete revolution is equivalent
    -- to 0 degrees and does not interpolate an accumulated angle backwards.
    context:setAnimationProgress(spinTrack(context), this.eye_spin_phase, true)
end

function M:states()
    local states = default.states(self)
    states[#states + 1] = spin_state
    return states
end

return M
