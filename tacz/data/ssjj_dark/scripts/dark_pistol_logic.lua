-- No projectile fallback: independent Java server strikes own all blade damage.
local pistol = {}
function pistol.shoot(api) end
function pistol.start_reload(api) return false end
return pistol
