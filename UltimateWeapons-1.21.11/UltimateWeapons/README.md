# UltimateWeapons (lõi + bộ nạp addon)

Framework plugin vũ khí tùy chỉnh cho **Paper/Leaf 1.21.11**, Java 21, build bằng Gradle (Kotlin DSL).
Jar lõi **không chứa vũ khí nào**. Mỗi vũ khí là một **addon jar** riêng, nạp lúc chạy.

## Build lõi

Yêu cầu: JDK 21 và Gradle 8.x (hoặc tạo wrapper một lần bằng `gradle wrapper`).

```
./gradlew build        # hoặc: gradle build
```

Jar nằm ở `build/libs/UltimateWeapons-<version>.jar`.

## Cài đặt

1. Chép jar vào `plugins/` của server, khởi động lại.
2. Lần đầu chạy, plugin tạo `config.yml`, thư mục `weapons/` và `addons/` trong `plugins/UltimateWeapons/`.
3. Nếu bạn nâng cấp từ bản cũ (chưa có addon), hãy xóa `config.yml` cũ để nhận các message mới.
4. `_example.yml.disabled` trong `weapons/` là file mẫu, không được nạp. Đổi đuôi thành `.yml` để dùng.

Thư mục `.addon-cache/` do lõi tự quản lý (bản copy của addon jar), không cần đụng vào.

## Lệnh và quyền

Quyền duy nhất: `uw.admin` (mặc định op). Lệnh `/uw` (alias `/ultimateweapons`):

| Lệnh | Tác dụng |
|---|---|
| `/uw give <id> [player]` | Đưa vũ khí cho bản thân hoặc người chơi chỉ định |
| `/uw list` | Liệt kê vũ khí: dòng 1 là id (click để nhận), dòng 2 là `Tên \| mô tả [nguồn]` (nguồn là built-in hoặc tên addon) |
| `/uw addons` | Liệt kê addon đã nạp (tên, version, type, tên jar) và lỗi của lần nạp gần nhất |
| `/uw reload` | Reload toàn bộ (xem quy trình bên dưới), báo số vũ khí, số addon và các lỗi |

## Quy trình `/uw reload`

1. Gọi `onPlayerCleanup` trên mọi vũ khí cho mọi người chơi online; hủy mọi charge/bossbar.
2. Xóa mọi instance vũ khí, gỡ mọi addon, đóng classloader, xóa cache.
3. Reload `config.yml`.
4. Đăng ký built-in, nạp lại addon trong `addons/`, quét `weapons/*.yml` dựng instance mới.
5. Báo kết quả. Cùng quy trình bước 1-2 chạy khi plugin tắt.

Lỗi của một addon hoặc một yml chỉ làm bỏ qua addon/file đó, không ảnh hưởng phần còn lại.
Item vũ khí còn trong túi người chơi mà addon của nó đã bị gỡ sẽ bị lõi bỏ qua, không gây lỗi.

## Cấu trúc yml của một vũ khí

Mỗi file `weapons/<id>.yml` là một vũ khí.

**Khóa bắt buộc**

| Khóa | Ý nghĩa |
|---|---|
| `id` | Id duy nhất (chữ, số, `_`, `-`) |
| `type` | Tên type do addon (hoặc built-in) đăng ký |
| `display-name` | Tên hiển thị (MiniMessage) |
| `description` | Mô tả ngắn (MiniMessage) |
| `material` | Tên Material của Bukkit |

**Tùy chọn chung** (mọi vũ khí đều dùng được)

| Khóa | Mặc định | Ý nghĩa |
|---|---|---|
| `cooldown-seconds` | không có | Hồi chiêu. Lõi không tự áp dụng, vũ khí tự đọc |
| `self-damage` | `false` | Cho phép gây sát thương lên chính chủ |
| `ignore-iframes` | `false` | Bỏ qua thời gian miễn sát thương |
| `ignore-armor` | `false` | Bỏ qua giáp |
| `custom-model-data` | không có | Số nguyên cho resource pack |
| `unbreakable` | `false` | Không hỏng độ bền |
| `lore` | không có | Danh sách dòng lore (MiniMessage) |
| `hide-attributes` | `false` | Ẩn dòng thuộc tính trong tooltip |

Chỉ số riêng của vũ khí (sát thương, bán kính, màu hex...) nằm cùng cấp hoặc gom trong một section,
đọc bằng `def.getDouble("damage", 7.0)` **tại thời điểm dùng**, không cache vào field.

Trùng `id` giữa hai yml: yml sau bị bỏ qua và báo lỗi. Một `type` dùng được cho nhiều yml khác nhau.

## Các hook của `UltimateWeapon`

Mọi hook chạy trên main thread và chỉ được gọi khi item/slot mang PDC `weapon_id` khớp.

| Hook | Khi nào |
|---|---|
| `onLeftClick(player, item, event)` | Click trái bằng tay chính, không gọi trùng |
| `onRightClick(player, item, event)` | Click phải bằng tay chính, không gọi trùng |
| `onSneak(player, item, sneaking)` | Bật/tắt sneak khi vũ khí ở tay chính |
| `onShoot(player, item, event)` | Bắn cung/nỏ là vũ khí. Projectile đã được gắn tag trước khi gọi |
| `onProjectileHit(owner, projectile, event)` | Projectile có tag chạm block/entity, chủ nhân online |
| `onTick(player, item, slot)` | Mỗi tick, khi vũ khí ở tay chính/tay phụ/slot giáp |
| `onEquip(player, item)` | Mặc vũ khí vào slot giáp (cũng gọi khi join, sau đổi world, sau respawn, sau reload) |
| `onUnequip(player, item)` | Tháo vũ khí khỏi slot giáp |
| `onGlideToggle(player, item, gliding)` | Bật/tắt bay lượn khi vũ khí ở slot ngực |
| `onPlayerCleanup(player)` | Người chơi thoát, chết, đổi world; reload, gỡ addon, disable. Dọn task, map, bossbar, entity, block tại đây |

Khác: `id()`, `createItem()`, `customizeItem(ItemMeta)`.

## Tiện ích dùng chung

| Tiện ích | Hàm chính |
|---|---|
| `DamageUtil` | `hit(attacker, target, amount, def)`, `isValidTarget(attacker, target, def)` |
| `ExplosionUtil` | `explode(source, loc, power, fire, breakBlocks, def)` |
| `CooldownManager` | `isOnCooldown`, `set`, `remainingMillis`, `clear` (qua `plugin.getCooldownManager()`) |
| `ChargeManager` | `start`, `isCharging`, `getChargeSeconds`, `hasReachedMinimum`, `stop`, `cancel` (qua `plugin.getChargeManager()`) |
| `RayUtil` | `entitiesAlongRay(...)`, `rayEnd(...)` |
| `ParticleUtil` | `parseHex`, `dust`, `line`, `circle` |
| `ItemKeys` | `readWeaponId`, `tagProjectile`, `readProjectileWeaponId`, `readProjectileOwner` (qua `plugin.getItemKeys()`) |

Mọi sát thương của vũ khí phải đi qua `DamageUtil.hit`, mọi vụ nổ qua `ExplosionUtil.explode`.

## Thêm vũ khí bằng addon

1. Build module trong `addons-workspace` (xem README của nó): `./gradlew collectAddons`.
2. Thả jar vào `plugins/UltimateWeapons/addons/`.
3. `/uw reload`.
4. `/uw addons` để kiểm tra addon đã nạp (hoặc xem lỗi), rồi `/uw give <id>`.

Addon không cần build lại plugin chính và không cần restart server. Thay jar addon bằng bản mới chỉ cần
copy đè vào `addons/` rồi `/uw reload`. **Chỉ thay đổi API/hook của lõi mới cần build lại lõi** (và khi đó
`ADDON_API_VERSION` có thể tăng, addon cũ khai báo api-version khác sẽ bị bỏ qua và báo lỗi).

### Định dạng addon jar

```
<id>.jar
├─ addon.yml
├─ com/example/ultimateweapons/weapons/<TenClass>.class
└─ weapons/<id>.yml
```

```yaml
name: storm_repeater        # a-z 0-9 _ -, duy nhất
version: 1.0.0
api-version: 1              # phải bằng UltimateWeaponsPlugin.ADDON_API_VERSION
weapons:
  - type: crossbow_repeater
    class: com.example.ultimateweapons.weapons.RepeaterCrossbow
```

Class khai báo phải kế thừa `UltimateWeapon` và có constructor công khai `(UltimateWeaponsPlugin, WeaponDefinition)`.
Trùng `type` giữa hai addon: addon nạp trước (theo tên file) thắng, addon sau bị bỏ qua type đó kèm cảnh báo.

## Vũ khí built-in (tùy chọn)

Nếu muốn nhúng vũ khí thẳng vào jar lõi: tạo class trong package `weapons`, thêm 1 dòng
`register("type", Class::new);` vào `BehaviorRegistry.registerBuiltIns()` và yml vào `src/main/resources/weapons/`.
Cách này cần build lại lõi và restart server.
