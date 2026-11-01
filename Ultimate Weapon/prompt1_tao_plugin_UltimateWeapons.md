Hãy viết cho tôi LÕI (framework) của một plugin Minecraft tên "UltimateWeapons", chạy trên Paper/Leaf 1.21.11 (Leaf là fork của Paper, KHÔNG dùng API riêng của Folia, dùng BukkitScheduler thường). Build bằng Gradle (Kotlin DSL), Java 21.

LƯU Ý QUAN TRỌNG: lần này CHỈ làm phần lõi, TUYỆT ĐỐI KHÔNG viết vũ khí cụ thể nào. Tôi sẽ thêm từng vũ khí sau bằng một cuộc trò chuyện khác (với một Claude khác), dựa trên HỢP ĐỒNG API bên dưới (người đó làm song song, không chờ bạn và không thấy code của bạn). Vì vậy:
- Lõi phải chạy được khi chưa có vũ khí nào (BehaviorRegistry rỗng, `/uw list` báo chưa có vũ khí).
- Tên class, chữ ký hàm và quy ước bên dưới phải được tuân thủ CHÍNH XÁC, vì Claude khác sẽ dựa vào đó để viết vũ khí.

Mục tiêu thiết kế: thêm vũ khí mới KHÔNG phải sửa code cũ; chỉnh chỉ số chỉ cần sửa yml rồi `/uw reload`.

Bạn là BÊN VIẾT LÕI: bạn phải triển khai ĐÚNG từng package, class, chữ ký hàm và ngữ nghĩa hook trong hợp đồng dưới đây. Không đổi tên, không đổi tham số, không bỏ hàm nào. Nếu bạn thấy cần thay đổi hợp đồng thì VẪN làm đúng hợp đồng, rồi ghi đề xuất thay đổi ở cuối câu trả lời. Phần mô tả chi tiết ở các mục Giai đoạn A/B bên dưới chỉ bổ sung cách triển khai; nếu mâu thuẫn với hợp đồng thì hợp đồng thắng.

==================================================
## HỢP ĐỒNG API CỦA PLUGIN (NGUỒN CHÂN LÝ DUY NHẤT)
==================================================
Plugin "UltimateWeapons" được làm bởi HAI bên độc lập, không nhìn thấy code của nhau: (1) bên viết LÕI plugin, (2) bên viết từng VŨ KHÍ. Hai bên phải khớp nhau tuyệt đối qua hợp đồng này (tên package, tên class, chữ ký hàm, ý nghĩa của từng hook). Hợp đồng này ĐƯỢC ƯU TIÊN hơn mọi mô tả khác nếu có chỗ mâu thuẫn.

### A. Cấu trúc package (cố định)
    com.example.ultimateweapons                UltimateWeaponsPlugin
    com.example.ultimateweapons.weapon         UltimateWeapon, WeaponDefinition, BehaviorRegistry, WeaponRegistry, ItemKeys
    com.example.ultimateweapons.weapons        (package chứa các vũ khí cụ thể; lõi để trống)
    com.example.ultimateweapons.manager        CooldownManager, ChargeManager
    com.example.ultimateweapons.util           DamageUtil, ExplosionUtil, RayUtil, ParticleUtil
    com.example.ultimateweapons.listener       WeaponListener
    com.example.ultimateweapons.command        UwCommand

### B. Chữ ký (chỉ liệt kê API công khai; thân hàm do lõi triển khai)

```java
// ===== com.example.ultimateweapons.UltimateWeaponsPlugin =====
public class UltimateWeaponsPlugin extends JavaPlugin {
    public WeaponRegistry getWeaponRegistry();
    public CooldownManager getCooldownManager();
    public ChargeManager getChargeManager();
    public ItemKeys getItemKeys();
}

// ===== com.example.ultimateweapons.weapon.WeaponDefinition =====
public final class WeaponDefinition {
    public String id();
    public String type();
    public Component displayName();            // đã parse từ MiniMessage (net.kyori.adventure.text.Component)
    public String description();               // chuỗi MiniMessage thô
    public Material material();
    public ConfigurationSection config();      // toàn bộ yml của vũ khí
    // Đọc chỉ số: LUÔN đọc tại thời điểm dùng, KHÔNG cache vào field để /uw reload có hiệu lực ngay.
    public double getDouble(String path, double fallback);
    public int getInt(String path, int fallback);
    public boolean getBoolean(String path, boolean fallback);
    public String getString(String path, String fallback);
    public List<String> getStringList(String path);
}

// ===== com.example.ultimateweapons.weapon.UltimateWeapon =====
public abstract class UltimateWeapon {
    protected final UltimateWeaponsPlugin plugin;
    protected final WeaponDefinition def;

    protected UltimateWeapon(UltimateWeaponsPlugin plugin, WeaponDefinition def);

    public final String id();                          // = def.id()
    public final ItemStack createItem();               // dựng item từ def (material, tên, lore, custom-model-data, unbreakable), ghi PDC weapon_id, rồi gọi customizeItem(meta)
    protected void customizeItem(ItemMeta meta) {}     // override khi cần chỉnh thêm (CrossbowMeta, ẩn attribute...)

    // Các hook, mặc định rỗng. Vũ khí chỉ override những hook cần dùng.
    public void onLeftClick(Player player, ItemStack item, PlayerInteractEvent event) {}
    public void onRightClick(Player player, ItemStack item, PlayerInteractEvent event) {}
    public void onSneak(Player player, ItemStack item, boolean sneaking) {}
    public void onShoot(Player player, ItemStack item, EntityShootBowEvent event) {}
    public void onProjectileHit(Player owner, Projectile projectile, ProjectileHitEvent event) {}
    public void onTick(Player player, ItemStack item, EquipmentSlot slot) {}
    public void onEquip(Player player, ItemStack item) {}
    public void onUnequip(Player player, ItemStack item) {}
    public void onGlideToggle(Player player, ItemStack item, boolean gliding) {}
    public void onPlayerCleanup(Player player) {}
}

// ===== com.example.ultimateweapons.weapon.BehaviorRegistry =====
public final class BehaviorRegistry {
    @FunctionalInterface
    public interface Factory { UltimateWeapon create(UltimateWeaponsPlugin plugin, WeaponDefinition def); }

    public static void register(String type, Factory factory);

    // Được gọi trong onEnable. Đây là NƠI DUY NHẤT để đăng ký vũ khí mới.
    static void registerBuiltIns() {
        // === ĐĂNG KÝ VŨ KHÍ MỚI: thêm 1 dòng ngay dưới dòng này ===
        // ví dụ: register("crossbow_repeater", RepeaterCrossbow::new);
    }
}

// ===== com.example.ultimateweapons.weapon.WeaponRegistry =====
public final class WeaponRegistry {
    public UltimateWeapon get(String id);              // null nếu không có
    public Collection<UltimateWeapon> all();
}

// ===== com.example.ultimateweapons.weapon.ItemKeys =====
public final class ItemKeys {
    public NamespacedKey weaponId();
    public NamespacedKey projectileWeaponId();
    public NamespacedKey projectileOwner();
    public String readWeaponId(ItemStack item);                         // null nếu không phải vũ khí
    public void tagProjectile(Entity entity, String weaponId, UUID owner);   // gắn tag cho entity vũ khí tự sinh (TNT, mũi tên phụ...)
    public String readProjectileWeaponId(Entity entity);                // null nếu không có tag
    public UUID readProjectileOwner(Entity entity);
}

// ===== com.example.ultimateweapons.manager.CooldownManager =====
public final class CooldownManager {
    public boolean isOnCooldown(Player p, String weaponId);
    public void set(Player p, String weaponId, long millis);
    public long remainingMillis(Player p, String weaponId);
    public void clear(Player p);
}

// ===== com.example.ultimateweapons.manager.ChargeManager =====
public final class ChargeManager {
    public void start(Player p, WeaponDefinition def, double minSeconds, double maxSeconds);  // bắt đầu sạc + hiện BossBar/ActionBar
    public boolean isCharging(Player p);
    public double getChargeSeconds(Player p);          // số giây đã sạc, đã giới hạn bởi maxSeconds; 0 nếu không đang sạc
    public boolean hasReachedMinimum(Player p);
    public double stop(Player p);                      // kết thúc, trả về số giây đã sạc, và dọn thanh sạc
    public void cancel(Player p);                      // hủy, dọn thanh sạc
}

// ===== com.example.ultimateweapons.util.DamageUtil =====
public final class DamageUtil {
    public static boolean hit(Player attacker, LivingEntity target, double amount, WeaponDefinition def);
    public static boolean isValidTarget(Player attacker, Entity target, WeaponDefinition def);
}

// ===== com.example.ultimateweapons.util.ExplosionUtil =====
public final class ExplosionUtil {
    public static void explode(Player source, Location loc, float power, boolean fire, boolean breakBlocks, WeaponDefinition def);
}

// ===== com.example.ultimateweapons.util.RayUtil =====
public final class RayUtil {
    public static List<LivingEntity> entitiesAlongRay(Location start, Vector direction, double range,
                                                     double halfWidth, double halfHeight, Predicate<Entity> filter);
    public static Location rayEnd(Location start, Vector direction, double range, boolean stopAtBlock);
}

// ===== com.example.ultimateweapons.util.ParticleUtil =====
public final class ParticleUtil {
    public static Color parseHex(String hex);                       // "#RRGGBB" -> org.bukkit.Color, sai định dạng thì trả về trắng
    public static void dust(Location loc, Color color, float size, int count);
    public static void line(Location from, Location to, Color color, double step);
    public static void circle(Location center, double radius, int points, Color color);
}
```

### C. Ngữ nghĩa bắt buộc của các hook (lõi phải triển khai đúng; vũ khí được phép tin vào)
- Mọi hook chạy trên MAIN THREAD. Hook chỉ được gọi khi item/slot mang PDC `weapon_id` khớp với vũ khí đó.
- `onLeftClick` / `onRightClick`: chỉ cho TAY CHÍNH (EquipmentSlot.HAND), không gọi trùng 2 lần cho cùng một lần bấm. Vũ khí có thể gọi `event.setCancelled(true)`.
- `onSneak`: mỗi lần bật/tắt sneak khi vũ khí đang ở tay chính (`sneaking` = trạng thái mới).
- `onShoot`: khi `EntityShootBowEvent` có cung/nỏ là vũ khí. Lõi ĐÃ gắn tag (`weapon_id` + chủ nhân) vào projectile TRƯỚC khi gọi hook này.
- `onProjectileHit`: khi projectile có tag chạm block hoặc entity và chủ nhân đang online. Vũ khí tự xóa projectile (`projectile.remove()`) nếu cần.
- `onTick`: MỖI 1 TICK, cho mỗi người chơi online đang cầm vũ khí ở tay chính/tay phụ hoặc đang mặc ở slot giáp; `slot` là EquipmentSlot tương ứng. Vũ khí muốn chạy thưa hơn thì tự kiểm tra `player.getTicksLived() % n`.
- `onEquip` / `onUnequip`: khi slot GIÁP đổi vũ khí (vào server đã mặc sẵn thì gọi `onEquip` ngay khi join).
- `onGlideToggle`: khi `EntityToggleGlideEvent` xảy ra với người chơi đang mặc vũ khí ở slot ngực (`gliding` = trạng thái mới).
- `onPlayerCleanup`: lõi gọi trên MỌI instance vũ khí đã đăng ký khi một người chơi thoát, chết, đổi world; và gọi cho mọi người chơi online khi `/uw reload` hoặc onDisable. Vì thế phải chịu được việc bị gọi dù người đó không dùng vũ khí này (khi đó không làm gì). Đây là nơi duy nhất dọn task, map, bossbar, trạng thái bay...
- `/uw reload` tạo INSTANCE vũ khí mới và bỏ instance cũ (sau khi đã gọi `onPlayerCleanup` trên instance cũ). Do đó vũ khí được phép giữ state theo người chơi trong map của instance, nhưng không được giữ chỉ số cấu hình trong field.
- Lõi KHÔNG tự áp dụng cooldown: vũ khí tự gọi `CooldownManager` nếu cần (đọc `cooldown-seconds` qua `def.getDouble`).
- `DamageUtil.hit` và `ExplosionUtil.explode` tự đọc các khóa `self-damage`, `ignore-iframes`, `ignore-armor` trong yml của vũ khí; vũ khí không cần xử lý lại. Sát thương luôn được tính cho `attacker`/`source`.

### D. Quy ước yml của vũ khí
- Vị trí nguồn: `src/main/resources/weapons/<id>.yml`. Plugin tự copy mọi `weapons/*.yml` trong jar ra `plugins/UltimateWeapons/weapons/` nếu chưa tồn tại, và nạp mọi file `*.yml` trong thư mục đó.
- Khóa bắt buộc: `id`, `type`, `display-name` (MiniMessage), `description` (MiniMessage), `material`.
- Tùy chọn chung (mọi vũ khí có thể dùng): `cooldown-seconds`, `self-damage` (mặc định false), `ignore-iframes` (mặc định false), `ignore-armor` (mặc định false), `custom-model-data`, `unbreakable`, `lore` (danh sách MiniMessage).
- Mọi chỉ số riêng của vũ khí (sát thương, bán kính, số lượng, màu hex `#RRGGBB`...) nằm trong yml cùng cấp với các khóa trên (hoặc nhóm gọn trong một section), đọc bằng `def.getXxx("khóa", mặcĐịnh)`.
- Một `type` (class Java) có thể dùng cho nhiều yml khác nhau; id là duy nhất theo từng yml.

==================================================
## CÁCH LÀM VIỆC (để tránh bị ngắt giữa chừng)
==================================================
- Chia câu trả lời làm 2 giai đoạn. Làm GIAI ĐOẠN A trước, xong thì viết đúng 1 dòng "Xong giai đoạn A, gõ 'tiếp' để làm giai đoạn B" rồi DỪNG và chờ tôi.
- Mỗi file viết ĐẦY ĐỦ nội dung (không viết tắt, không "..."), ghi rõ đường dẫn file ngay trước nội dung, và đánh số thứ tự file (File 1, File 2...). Nếu bị ngắt, tôi sẽ nói "tiếp tục từ file số N".
- Không giải thích dài dòng, ưu tiên code. Comment trong code bằng tiếng Việt, tên class/biến bằng tiếng Anh.

==================================================
## YÊU CẦU BUILD
==================================================
- `paper-api:1.21.11-R0.1-SNAPSHOT` khai báo `compileOnly`, repo papermc. Không NMS, không reflection vào internals, không API deprecated.
- Các file: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`. Chạy `./gradlew build` ra jar ở `build/libs`. Không cần shadow nếu không có dependency ngoài.
- `plugin.yml`: name `UltimateWeapons`, `api-version: '1.21'`, command `uw` (aliases: `ultimateweapons`), permission `uw.admin` (mặc định op).
- Package gốc: `com.example.ultimateweapons` (tôi sẽ đổi sau). Các vũ khí sau này sẽ nằm ở package con `weapons`.
- Text dùng Adventure Component; message trong config dùng MiniMessage.

==================================================
## GIAI ĐOẠN A: build + framework + lệnh
==================================================

1. Các file build và `plugin.yml`.

2. `UltimateWeaponsPlugin` (main class): onEnable/onDisable, nạp config, khởi tạo các registry/manager, đăng ký listener và lệnh. Có getter cho các manager (`getWeaponRegistry()`, `getCooldownManager()`, `getChargeManager()`, `getItemKeys()`...). onDisable phải dọn mọi task/state.

3. `ItemKeys` (package `weapon`): nơi tập trung các NamespacedKey và các hàm đọc/ghi PDC đúng như hợp đồng (`weaponId()`, `projectileWeaponId()`, `projectileOwner()`, `readWeaponId`, `tagProjectile`, `readProjectileWeaponId`, `readProjectileOwner`), truy cập qua `plugin.getItemKeys()`.

4. **`UltimateWeapon`** (abstract class). Constructor: `(UltimateWeaponsPlugin plugin, WeaponDefinition def)`; hai field `protected final plugin` và `protected final def`. Có các hook rỗng (không abstract) để subclass override, ĐÚNG chữ ký sau:
   - `onLeftClick(Player player, ItemStack item, PlayerInteractEvent event)`
   - `onRightClick(Player player, ItemStack item, PlayerInteractEvent event)`
   - `onSneak(Player player, ItemStack item, boolean sneaking)`
   - `onShoot(Player player, ItemStack item, EntityShootBowEvent event)`
   - `onProjectileHit(Player owner, Projectile projectile, ProjectileHitEvent event)`
   - `onTick(Player player, ItemStack item, EquipmentSlot slot)` gọi định kỳ khi vũ khí đang được cầm (tay chính/phụ) hoặc đang mặc (slot giáp)
   - `onEquip(Player player, ItemStack item)` / `onUnequip(Player player, ItemStack item)` cho slot giáp
   - `onGlideToggle(Player player, ItemStack item, boolean gliding)`
   - `onPlayerCleanup(Player player)`: gọi khi người chơi thoát, chết, đổi world, và khi plugin reload/disable
   Ngoài ra: `public String id()`, `public ItemStack createItem()` (dựng item từ definition: material, tên, lore từ description + `lore` tùy chọn trong yml, custom-model-data tùy chọn, unbreakable tùy chọn, ẩn attribute tùy chọn, ghi PDC `weapon_id`), và hook `protected void customizeItem(ItemMeta meta)` để subclass chỉnh thêm.

5. **`WeaponDefinition`**: id, type, displayName, description, material và `ConfigurationSection config()` của vũ khí đó. QUY ƯỚC (ghi thành comment ở đầu class): subclass luôn đọc chỉ số qua `def.config().getDouble("damage", 7.0)` TẠI THỜI ĐIỂM DÙNG, KHÔNG cache vào field, để `/uw reload` có hiệu lực ngay. Không lưu chỉ số vào item, item chỉ chứa `weapon_id`.

6. **`BehaviorRegistry`**: map `type -> factory (plugin, def) -> UltimateWeapon`. Đăng ký vũ khí mới bằng ĐÚNG 1 dòng, ví dụ `register("crossbow_repeater", RepeaterCrossbow::new)`. Lần này để rỗng: hàm `registerBuiltIns()` chỉ chứa dòng comment marker `// === ĐĂNG KÝ VŨ KHÍ MỚI: thêm 1 dòng ngay dưới dòng này ===` đúng như hợp đồng, và được gọi trong onEnable. Một type có thể dùng cho nhiều file yml khác nhau.

7. **`WeaponRegistry`**: map `id -> UltimateWeapon`, dựng từ thư mục `plugins/UltimateWeapons/weapons/*.yml`.
   - Mỗi yml có các khóa bắt buộc: `id`, `type`, `display-name`, `description`, `material`; phần còn lại là chỉ số riêng của vũ khí.
   - Lỗi ở 1 file (thiếu khóa, type không tồn tại, material sai, yml hỏng) chỉ làm bỏ qua file đó và báo rõ lý do trong console; không làm hỏng cả quá trình load/reload.
   - Khi plugin khởi động, tự động copy MỌI file `weapons/*.yml` nằm trong jar (quét các entry của jar, KHÔNG hard-code danh sách tên file) ra `plugins/UltimateWeapons/weapons/`, chỉ ghi nếu file đó chưa tồn tại (không đè chỉnh sửa của tôi). Nhờ vậy sau này thêm vũ khí mới chỉ cần thêm yml vào `src/main/resources/weapons/` mà không phải sửa code này.
   - Copy thêm `_example.yml.disabled` làm mẫu (file này không được nạp vì không kết thúc bằng `.yml`).

8. **`WeaponListener` DUY NHẤT**:
   - Nhận diện vũ khí bằng PersistentDataContainer key `weapon_id` (KHÔNG dựa vào tên/lore), rồi gọi đúng hook của vũ khí đó.
   - Xử lý: PlayerInteractEvent (trái/phải, cả tay chính và tay phụ, chống gọi trùng 2 lần), PlayerToggleSneakEvent, EntityShootBowEvent (ghi `weapon_id` + uuid người bắn vào PDC của projectile), ProjectileHitEvent (đọc PDC để biết vũ khí nào và chủ nhân là ai, gọi `onProjectileHit`), EntityToggleGlideEvent, PlayerArmorChangeEvent của Paper (cho onEquip/onUnequip), PlayerQuitEvent/PlayerDeathEvent/PlayerChangedWorldEvent (gọi `onPlayerCleanup`).
   - Một task lặp DUY NHẤT lo `onTick`, chỉ xử lý người chơi đang cầm hoặc mặc vũ khí, kiểm tra rẻ (đọc PDC), không quét thừa để không gây lag.

9. **Lệnh `/uw`** (một class, có TabCompleter, kiểm tra permission `uw.admin`):
   - `/uw give <id> [player]`: tab-complete id lấy từ WeaponRegistry và tên người chơi online.
   - `/uw list`: mỗi vũ khí hiển thị 2 dòng: dòng 1 là `id` (click vào chạy `/uw give <id>` cho bản thân), dòng 2 là `Tên | mô tả`. Nếu chưa có vũ khí thì báo "chưa có vũ khí nào".
   - `/uw reload`: reload `config.yml`, nạp lại toàn bộ `weapons/*.yml`, tạo lại instance, hủy mọi task/charge đang chạy, gọi `onPlayerCleanup` cho mọi người chơi online. Báo số vũ khí đã load và liệt kê file lỗi (nếu có).
   - Mọi message lấy từ `config.yml` (MiniMessage), có prefix.

10. `config.yml` (prefix, messages, debug) và `_example.yml.disabled` trong `src/main/resources`. File mẫu phải đầy đủ các khóa bắt buộc kèm comment giải thích từng khóa, và các tùy chọn chung sau (mọi vũ khí đều có thể dùng):
    - `cooldown-seconds`
    - `self-damage` (mặc định false)
    - `ignore-iframes` (mặc định false)
    - `ignore-armor` (mặc định false)
    - `custom-model-data` (tùy chọn), `unbreakable` (tùy chọn), `lore` (tùy chọn, danh sách MiniMessage)

Sau Giai đoạn A, viết đúng 1 dòng "Xong giai đoạn A, gõ 'tiếp' để làm giai đoạn B" rồi DỪNG.

==================================================
## GIAI ĐOẠN B: tiện ích dùng chung + README
==================================================
Viết các tiện ích mà mọi vũ khí sau này dùng lại. Dùng ĐÚNG các chữ ký sau (có thể thêm hàm phụ, nhưng không đổi tên/chữ ký những hàm này):

- **`DamageUtil`** (static)
  - `boolean hit(Player attacker, LivingEntity target, double amount, WeaponDefinition def)`: MỌI sát thương của vũ khí phải đi qua hàm này. Sát thương luôn được tính cho `attacker` (kill credit, rơi đồ, XP đúng; dùng `target.damage(amount, attacker)` hoặc DamageSource của Paper). Đọc tùy chọn trong yml của vũ khí: `self-damage`, `ignore-iframes` (setNoDamageTicks(0) trước khi damage), `ignore-armor`. Trả về true nếu đã gây damage.
  - `boolean isValidTarget(Player attacker, Entity target, WeaponDefinition def)`: bỏ qua chính chủ (trừ khi `self-damage`), thú đã thuần hóa của chủ, armor stand, entity không nên bị đánh, người chơi spectator/creative.
- **`ExplosionUtil`** (static)
  - `void explode(Player source, Location loc, float power, boolean fire, boolean breakBlocks, WeaponDefinition def)`: dùng `world.createExplosion(source, loc, power, fire, breakBlocks)` để sát thương nổ tính cho người dùng; chặn self-damage theo config của vũ khí (đánh dấu nguồn nổ để `EntityDamageEvent` nhận ra và hủy).
- **`CooldownManager`**: `boolean isOnCooldown(Player p, String weaponId)`, `void set(Player p, String weaponId, long millis)`, `long remainingMillis(Player p, String weaponId)`, `void clear(Player p)`.
- **`ChargeManager`**: `void start(Player p, WeaponDefinition def, double minSeconds, double maxSeconds)`, `boolean isCharging(Player p)`, `double getChargeSeconds(Player p)`, `boolean hasReachedMinimum(Player p)`, `double stop(Player p)` (trả về số giây đã sạc và dọn), `void cancel(Player p)`. Hiển thị thanh sạc bằng BossBar hoặc ActionBar (đổi màu khi đủ mức tối thiểu); tự dọn khi người chơi thoát/chết/đổi slot/reload.
- **`RayUtil`** (static): `List<LivingEntity> entitiesAlongRay(Location start, Vector direction, double range, double halfWidth, double halfHeight, Predicate<Entity> filter)` (hộp quanh đường thẳng, dùng được cho nhát chém cao nhiều block), `Location rayEnd(Location start, Vector direction, double range, boolean stopAtBlock)`.
- **`ParticleUtil`** (static): `Color parseHex(String hex)` (chấp nhận "#RRGGBB"), `void dust(Location loc, Color color, float size, int count)`, `void line(Location from, Location to, Color color, double step)`, `void circle(Location center, double radius, int points, Color color)`.
- **`README.md`**: cách build, cài đặt, lệnh/permission, cấu trúc yml của một vũ khí (các khóa bắt buộc và tùy chọn chung), bảng liệt kê các hook và tiện ích, và mục "Cách thêm vũ khí mới" gồm đúng 3 bước: (1) tạo class kế thừa `UltimateWeapon` trong package `weapons`, (2) thêm 1 dòng đăng ký vào `BehaviorRegistry`, (3) thêm file yml vào `src/main/resources/weapons/`. Ghi rõ: `/uw reload` chỉ áp dụng cho config; vũ khí có logic code mới thì cần thay jar và restart server.

==================================================
## YÊU CẦU CHẤT LƯỢNG
==================================================
- Không để NPE khi item không hợp lệ, người chơi offline, world unload.
- Mọi state theo người chơi (task, map, bossbar...) phải được dọn khi người chơi thoát/chết/đổi world/reload/disable.
- Toàn bộ code phải compile được với Paper API 1.21.11; kiểm tra kỹ import và tên API.
- Cuối cùng: liệt kê những chỗ bạn không chắc về API và cần tôi test trên server (ví dụ PlayerArmorChangeEvent, createExplosion với source, DamageSource).
