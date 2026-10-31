# CBC Programmable Fuzes

## 1.3.14 - exact addon projectile launch data

- `getLoadedProjectileInfo()` now reports the loaded big-cannon projectile's entity id, mass,
  physical diameter, minimum charge and projectile-specific added charge power.
- `physicalDiameterMetres` is the entity hitbox for inspection only. External ballistic solvers
  must continue to use CBC Realistic Ballistics' nominal calibre overrides where applicable.
- External fire-control programs can therefore include Military Supplement APFSDS' hidden `+4`
  charge power instead of increasingly aiming too high at long range.

## 1.3.13 - addon projectile tail-module transfer

- The mounted-cannon firing boundary now copies the trajectory-correction module into the live
  projectile even when an addon overrides CBC's projectile factory without calling its parent.
- This restores UUID telemetry and active correction for Military Supplement APFSDS and CBC:Neo
  Warfare HEAP/cumulative shells, while leaving ordinary CBC projectile creation unchanged.

## 1.3.12 - gravity-limited trajectory correction

- Pitch-up correction now spends part of the available fin authority overcoming the projectile's
  measured downward acceleration. As speed falls, climbing becomes progressively harder and the
  correction module can no longer recycle falling velocity into endless upward hops.
- Steering now applies a small induced-drag energy loss, strongest during pitch-up manoeuvres.
- The calculation uses acceleration observed from the installed projectile physics, preserving
  compatibility with CBC Realistic Ballistics instead of assuming one fixed gravity value.

## 1.3.11 - live in-flight fuze programming

- Programmable fuzes now have a communication channel (`unsecured` by default).
- A CC:Tweaked computer can list flying fuzes by channel and change their timer,
  proximity radius/state, persistence and channel by projectile UUID.
- `getFuzeTelemetry` reports the live fuze settings, arming state, remaining timer,
  position and velocity.
- In-flight commands use a thread-safe latest-state mailbox instead of CC's
  Minecraft-thread task queue. A computer accelerated by Sable-cc-tick-sync can
  therefore transmit at the construction update rate without accumulating stale
  commands; the newest state is applied before the projectile's next fuze check.
- Changing one in-flight parameter does not reset an unrelated timer or proximity
  arming state.

## 1.3.10 - resilient correction telemetry

- Live correction telemetry now survives short unloaded-chunk scheduling gaps for up to 30 seconds; real entity removal still clears it immediately.

## 1.3.9 - correction-module persistence

- `setCorrectionPersistent(boolean)` lets the active correction module independently prevent projectile despawn.
- A persistent fuze and a persistent correction module share one removal guard, so enabling both does not duplicate or conflict.

## 1.3.8 - behavior-preserving performance work

- The proximity ray grid runs only when a conservative live terrain/Sable broad phase
  reports a candidate. Empty air skips the grid; unknown chunks or an incompatible
  Sable API retain the original exact raycasts. Living-entity checks are never skipped.
- Ray offsets and forward reach are cached in a bounded 32-radius cache. Grid spacing,
  ordering, detonation distance, tick frequency, timers and impact behavior are unchanged.
- Telemetry lookup/retargeting by UUID no longer sweeps all projectiles. Per-channel
  sorted indexes avoid global filtering/sorting; snapshots remain fresh per request.
  Full stale-entry cleanup is limited to once per second on list requests, direct reads
  still reject expired entries immediately, and server shutdown clears both indexes.
- Settings reads avoid building a redundant defaults record for fully configured fuzes.
- Guidance equations, steering frequency, range bonus, saved NBT, recipes and Lua API
  are unchanged. CBC Realistic Ballistics and other installed mods are not modified.

Verification: `gradlew build` runs deterministic grid/envelope and telemetry-index tests.
These are offline regressions, not an in-game TPS benchmark. No TPS fault was confirmed.

Programmable combined proximity and timed fuzes for **Create: Big Cannons**, controlled through **CC:Tweaked**.

Повний український перелік доданих Lua-методів і змінених точок CBC/Minecraft:
[`METHODS_UK.md`](METHODS_UK.md).

Supported versions:

- Minecraft 1.21.1
- NeoForge 21.1.235
- Create 6.0.10
- Create: Big Cannons 5.11.7
- CC:Tweaked 1.120.0
- Sable 2.0.3

---

## English documentation

### Connecting a computer

Connect a CC:Tweaked computer through a wired modem to either:

1. A placed projectile containing the **Programmable Fuze**.
2. An assembled CBC cannon mount with a compatible loaded projectile.

The mod does not replace peripheral methods provided by CBC or its addons.

### Finding the peripheral

```lua
local fuze = peripheral.find("programmable_fuze")

if not fuze then
    error("Programmable fuze peripheral not found")
end
```

### API methods

| Method | Description |
|---|---|
| `hasProgrammableFuze()` | Returns `true` when a compatible loaded projectile contains the programmable fuze. |
| `setProximity(radius, active)` | Sets the proximity radius and enables or disables proximity detonation. |
| `setTimer(seconds, active)` | Sets the timer delay and enables or disables timed detonation. |
| `setPersistent(active)` | Enables or disables protection from projectile lifetime despawn. |
| `setFuzeChannel(channel)` | Sets the programmable fuze channel (`unsecured` by default). |
| `listFuzeProjectiles(channel)` | Lists flying programmable fuzes on the channel. |
| `getFuzeTelemetry(channel, id)` | Returns live settings, timer, arming state, position and velocity. |
| `setFuzeProjectileTimer(channel, id, seconds, active)` | Changes or restarts a flying projectile's timer. |
| `setFuzeProjectileProximity(channel, id, radius, active)` | Changes proximity detonation in flight. |
| `setFuzeProjectilePersistent(channel, id, active)` | Changes lifetime-despawn protection in flight. |
| `setFuzeProjectileChannel(channel, id, newChannel)` | Moves a flying fuze to another channel. |
| `setFuzeProjectileSettings(...)` | Atomically changes every in-flight fuze parameter. |
| `hasTrajectoryCorrectionModule()` | Checks for an independently installed active tail module. |
| `setCorrectionChannel(channel)` | Sets its communication channel (`unsecured` by default). |
| `setCorrectionPersistent(active)` | Independently enables or disables lifetime-despawn protection on the correction module. |
| `setImpactCoordinates(x, y, z)` | Programs the desired impact coordinates. |
| `listCorrectionProjectiles(channel)` | Lists the UUIDs of all flying modules on a channel. |
| `getCorrectionTelemetry(channel, id)` | Returns `success, telemetry`; telemetry contains position, velocity, speed, target, steering state and remaining correction authority. |
| `getLoadedProjectileInfo()` | Returns `success, info` for the next projectile in firing order, including its ID, fuze, tracer and correction module. |
| `getLoadedPropellantInfo()` | Returns `success, info` with every loaded propellant segment plus CBC's total charge power, stress, spread and effective recoil. |
| `getCannonInfo()` | Returns `success, info` with cannon type, material, length, weight, orientation and material-specific limits. |

The three inspection methods are read-only and do not unload ammunition or alter the cannon. They work on an
assembled cannon mount. For autocannons, `getLoadedPropellantInfo()` reports that the round uses integrated
propellant, since there are no separately loaded powder charges.

```lua
local cannon = peripheral.find("programmable_fuze")
assert(cannon, "Cannon peripheral not found")

local hasRound, round = cannon.getLoadedProjectileInfo()
if hasRound then
    print("Next projectile:", round.id)
end

local hasChargeData, charge = cannon.getLoadedPropellantInfo()
if hasChargeData then
    print("Charge power:", charge.totalChargePower or 0)
    print("Propellant stress:", charge.totalStress or 0)
end

local hasCannon, info = cannon.getCannonInfo()
if hasCannon then
    print("Material:", info.material)
    print("Length:", info.lengthBlocks, "blocks")
    print("Pitch axis to muzzle:", info.muzzleDistanceBlocks, "blocks")
end
```

Install the module by right-clicking the rear face of a placed fuzed CBC projectile. Remove it with
Shift + empty-hand right click on the same face. The same interaction also works on supported CBC
projectiles without a fuze: the tail module is stored independently from the fuze.

The module preserves the ascending ballistic arc and starts active steering exactly at the apex.
By default the fins provide about 10% additional range and may turn the velocity vector by up to
6 degrees in any 3D direction per kilometre flown after the apex. This rate is configurable with
`turnDegreesPerKilometre`.

```lua
local shell = peripheral.find("programmable_fuze")
assert(shell and shell.hasTrajectoryCorrectionModule(), "Tail module not found")

shell.setCorrectionChannel("battery_alpha")
shell.setImpactCoordinates(1200, 85, -340)

for _, projectile in ipairs(shell.listCorrectionProjectiles("battery_alpha")) do
    local ok, telemetry = shell.getCorrectionTelemetry("battery_alpha", projectile.id)
    if ok then
        print(projectile.id, telemetry.speed, telemetry.targetDistance)
        print("guiding", telemetry.guidanceActive,
              "authority", telemetry.correctionDistanceRemaining)
        shell.setCorrectionProjectileTarget(
            "battery_alpha", projectile.id, targetX, targetY, targetZ)
    end
end
```

#### Proximity detonation

```lua
fuze.setProximity(2, true)  -- Detonate within 2 metres
fuze.setProximity(0, true)  -- Contact detonation
fuze.setProximity(2, false) -- Disable proximity mode
```

The default allowed radius is **0–64 blocks**, but server administrators can change these limits in the mod configuration.

#### Timed detonation

```lua
fuze.setTimer(8, true)   -- Detonate after 8 seconds
fuze.setTimer(0, true)   -- Detonate immediately after firing
fuze.setTimer(8, false)  -- Disable the timer
```

The timer has a resolution of **0.05 seconds**. The default allowed range is **0–86,400 seconds**.

#### In-flight programming

```lua
local fuze = peripheral.find("programmable_fuze")
fuze.setFuzeChannel("air_defence") -- run before firing

-- Optional: match a Sable construction's 100 Hz computer update rate.
if sableSync then sableSync.setHighFrequency(true) end

while true do
    for _, shell in ipairs(fuze.listFuzeProjectiles("air_defence")) do
        local ok, live = fuze.getFuzeTelemetry("air_defence", shell.id)
        if ok then
            -- This becomes the new remaining time at the next fuze check.
            fuze.setFuzeProjectileTimer("air_defence", shell.id, 0.25, true)
            fuze.setFuzeProjectileProximity("air_defence", shell.id, 3.0, true)
        end
    end
    sleep(0)
end
```

`setFuzeProjectileSettings(channel, id, timerSeconds, timerActive,
proximityRadius, proximityActive, persistent)` replaces every configurable
detonation parameter atomically. Repeating a timer command intentionally restarts
that projectile's remaining timer. The high-frequency path changes command
reception only; detonation checks retain CBC's authoritative projectile tick.

#### Projectile persistence

```lua
fuze.setPersistent(true)  -- Prevent lifetime despawn
fuze.setPersistent(false) -- Use normal projectile lifetime
```

This option only prevents removal by a projectile lifetime or despawn timer. It does not stop normal impact or detonation.

### Combined configuration example

```lua
local fuze = peripheral.find("programmable_fuze")

if not fuze then
    error("Programmable fuze peripheral not found")
end

if not fuze.hasProgrammableFuze() then
    error("No loaded projectile with a programmable fuze")
end

fuze.setProximity(3, true)
fuze.setTimer(12, true)
fuze.setPersistent(true)

print("Fuze configured")
```

Proximity and timer modes use **OR logic**: the projectile detonates when the first enabled condition is reached.

---

## Русская документация

### Подключение компьютера

Подключите компьютер CC:Tweaked через проводной модем к одному из следующих объектов:

1. Установленному снаряду с **программируемым взрывателем**.
2. Собранной орудийной установке CBC, внутри которой находится совместимый снаряд.

Мод не заменяет методы периферии, добавленные CBC или его аддонами.

### Поиск периферии

```lua
local fuze = peripheral.find("programmable_fuze")

if not fuze then
    error("Программируемый взрыватель не найден")
end
```

### Методы API

| Метод | Описание |
|---|---|
| `hasProgrammableFuze()` | Возвращает `true`, если в совместимом снаряде установлен программируемый взрыватель. |
| `setProximity(radius, active)` | Устанавливает радиус и включает или отключает дистанционный подрыв. |
| `setTimer(seconds, active)` | Устанавливает задержку и включает или отключает подрыв по таймеру. |
| `setPersistent(active)` | Включает или отключает защиту снаряда от исчезновения по времени жизни. |

#### Дистанционный подрыв

```lua
fuze.setProximity(2, true)  -- Подрыв на расстоянии 2 метров
fuze.setProximity(0, true)  -- Контактный подрыв
fuze.setProximity(2, false) -- Отключить дистанционный режим
```

Стандартный допустимый радиус — **от 0 до 64 блоков**. Администратор сервера может изменить эти ограничения в конфигурации мода.

#### Подрыв по таймеру

```lua
fuze.setTimer(8, true)   -- Подрыв через 8 секунд
fuze.setTimer(0, true)   -- Мгновенный подрыв после выстрела
fuze.setTimer(8, false)  -- Отключить таймер
```

Точность таймера составляет **0,05 секунды**. Стандартный допустимый диапазон — **от 0 до 86 400 секунд**.

#### Защита от деспавна

```lua
fuze.setPersistent(true)  -- Запретить исчезновение по времени жизни
fuze.setPersistent(false) -- Использовать обычное время жизни снаряда
```

Эта настройка предотвращает только удаление снаряда таймером жизни или деспавна. Она не блокирует столкновение или нормальный подрыв.

### Пример комбинированной настройки

```lua
local fuze = peripheral.find("programmable_fuze")

if not fuze then
    error("Программируемый взрыватель не найден")
end

if not fuze.hasProgrammableFuze() then
    error("Снаряд с программируемым взрывателем не загружен")
end

fuze.setProximity(3, true)
fuze.setTimer(12, true)
fuze.setPersistent(true)

print("Взрыватель настроен")
```

Дистанционный режим и таймер работают по логике **ИЛИ**: снаряд взорвётся при выполнении первого активного условия.

---

## Server configuration

The Create-style configuration screen allows server administrators to change:

- Global feature availability.
- Default fuze settings.
- Minimum and maximum proximity radius.
- Minimum and maximum timer delay.
- Availability of CC:Tweaked programming and projectile persistence.
- Tail-fin range multiplier, activation height, turn rate, total angular authority and maximum
  correction distance.
