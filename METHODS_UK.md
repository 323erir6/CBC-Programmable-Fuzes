# Методи CBC Programmable Fuzes

## Lua API CC:Tweaked

Мод додає до сумісної периферії типи `programmable_fuze`,
`cbc_programmable_fuze` і `trajectory_correction` та 14 методів:

| Метод | Результат і призначення |
|---|---|
| `hasProgrammableFuze()` | Повертає `true`, якщо наступний сумісний снаряд має програмований підривник. |
| `setProximity(radius, active)` | Записує радіус у блоках і вмикає або вимикає безконтактний підрив. |
| `setTimer(seconds, active)` | Записує час і вмикає або вимикає таймерний підрив; точність становить один тик, тобто `0.05` с. |
| `setPersistent(active)` | Вмикає або вимикає захист снаряда від видалення після завершення стандартного часу життя. |
| `hasTrajectoryCorrectionModule()` | Повертає `true`, якщо наступний снаряд має хвостовий модуль корекції. |
| `setCorrectionChannel(channel)` | Задає канал зв'язку модуля корекції; порожнє значення стає `unsecured`, довжина обмежена 64 символами. |
| `setCorrectionPersistent(active)` | Окремо вмикає або вимикає захист від деспавну для модуля активної корекції. Разом із підривником працює як логічне «або». |
| `setImpactCoordinates(x, y, z)` | Записує світові координати бажаної точки влучання. |
| `listCorrectionProjectiles(channel)` | Повертає список активних снарядів каналу з полями `id`, `type`, `ageTicks`. |
| `getCorrectionTelemetry(channel, id)` | Повертає `success, telemetry`: позицію, швидкість, ціль, відстань, стан наведення та залишок ресурсу корекції. |
| `setCorrectionProjectileTarget(channel, id, x, y, z)` | Оновлює координати цілі вже випущеного снаряда за його каналом і UUID; повертає `false`, якщо снаряд уже недоступний. |
| `getLoadedProjectileInfo()` | Повертає `success, info` про наступний снаряд: ID, підривник, трасер, хвостовий модуль та пов'язані дані. |
| `getLoadedPropellantInfo()` | Повертає `success, info` про сегменти заряду та підсумкові power, stress, spread і recoil CBC. |
| `getCannonInfo()` | Повертає `success, info` про гармату, матеріал, орієнтацію, обмеження та `muzzleDistanceBlocks` від pitch-осі до дула. |

Методи `set*` повертають `true` після успішного запису або створюють Lua-помилку,
якщо функція заборонена серверною конфігурацією чи потрібного модуля немає.

## Перевизначені методи підривника CBC

Клас `ProgrammableFuzeItem` перевизначає шість методів `FuzeItem`:

| Метод | Що робить мод |
|---|---|
| `onProjectileTick(...)` | Озброює proximity-режим і відраховує таймер до підриву. |
| `onProjectileClip(...)` | Перевіряє блоки та живі сутності в радіусі proximity, включно з трансформованими блоками Sable. |
| `onProjectileImpact(...)` | Виконує контактний підрив активного не-базового proximity-підривника. |
| `onProjectileExpiry(...)` | Або підриває активний підривник, або дозволяє persistent-снаряду залишитися. |
| `canLingerInGround(...)` | Дозволяє снаряду лежати до таймерного підриву чи залишатися через persistent-режим. |
| `appendHoverText(...)` | Додає до підказки поточні proximity, timer, persistent і computer-стани. |

## Змінені точки CBC і Minecraft

Мод не замінює ці методи повністю, а додає код через mixin у вказаних точках:

| Клас і метод | Додана поведінка |
|---|---|
| `AbstractCannonProjectile.tick()` | Перед фізикою застосовує хвостову корекцію та оновлює телеметрію. |
| `Entity.remove(...)` | Не дає persistent-снаряду зникнути через `DISCARDED` і видаляє завершену телеметрію. |
| `BigCannonProjectileBlockEntity.saveAdditional(...)` | Зберігає хвостовий модуль у NBT. |
| `BigCannonProjectileBlockEntity.writeSafe(...)` | Переносить хвостовий модуль у safe-NBT конструкції. |
| `BigCannonProjectileBlockEntity.loadAdditional(...)` | Відновлює хвостовий модуль із NBT. |
| `FuzedBlockEntity.getFuze()` | Переносить налаштування корекції з хвостового модуля до carrier-даних підривника. |
| `FuzedBlockEntity.setFuze(...)` | Синхронізує carrier-дані нового підривника з хвостовим модулем. |
| `FuzedProjectileBlock.useWithoutItem(...)` | Дозволяє Shift-кліком зняти хвостовий модуль. |
| `ProjectileBlock/FuzedProjectileBlock.getHandloadingInfo(...)` | Додає хвостовий модуль до інформації ручного заряджання. |
| `ProjectileBlock/FuzedProjectileBlock.getExtractedItem(...)` | Зберігає налаштування модуля у витягнутому предметі. |
| `ProjectileBlock/FuzedProjectileBlock.getCloneItemStack(...)` | Зберігає налаштування при клонуванні блока. |
| Три overload-и `ProjectileBlock/FuzedProjectileBlock.getProjectile(...)` | При створенні летючого снаряда приєднують до нього корекцію з блока або предмета. |
| `FuzedBigCannonProjectile.setFuze(...)` | Відновлює корекцію з carrier-даних установленого підривника. |
| `NeoPeripheral CannonMountPeripheral.getAdditionalTypes()` | Оголошує додаткові типи периферії для зібраної гармати. |

Окремо мод реєструє block capability для доступу CC:Tweaked і обробник
`RightClickBlock`, який установлює або знімає хвостовий модуль. До
`BigCannonProjectileBlockEntity` додаються внутрішні методи читання та запису
цього модуля, а до `AbstractCannonProjectile` - transient-кеш стану корекції.
