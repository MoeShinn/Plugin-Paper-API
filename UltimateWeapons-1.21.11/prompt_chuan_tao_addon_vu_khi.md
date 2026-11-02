Hãy viết MỘT vũ khí tối thượng mới cho plugin Minecraft "UltimateWeapons" (Paper/Leaf 1.21.11, Java 21, Gradle), đóng gói thành một ADDON JAR.

Bạn KHÔNG có mã nguồn của plugin. Toàn bộ API bạn được phép dùng nằm trong HỢP ĐỒNG API bên dưới. Lõi plugin được người khác viết riêng, song song, theo đúng hợp đồng này, và addon của bạn sẽ được thả vào server sau. Vì vậy:
- Chỉ gọi những class/hàm có trong hợp đồng, đúng package và đúng chữ ký. KHÔNG bịa thêm API của plugin.
- KHÔNG viết lại hay giả lập các class của lõi (UltimateWeapon, DamageUtil...). Chỉ import chúng.
- Bạn không thể chạy hay compile code, nên hãy tự rà soát kỹ import, tên API Paper và kiểu dữ liệu trước khi trả lời.

Bạn là BÊN VIẾT VŨ KHÍ: chỉ được dùng đúng những gì có trong hợp đồng dưới đây (đặc biệt tuân thủ mục E6). Bên kia sẽ triển khai lõi và bộ nạp addon y hệt hợp đồng này.

==================================================
## HỢP ĐỒNG API CỦA PLUGIN (NGUỒN CHÂN LÝ DUY NHẤT) - PHIÊN BẢN 2, CÓ ADDON
==================================================
Plugin "UltimateWeapons" được làm bởi HAI bên độc lập, không nhìn thấy code của nhau: (1) bên viết LÕI plugin (gồm bộ nạp addon), (2) bên viết từng VŨ KHÍ, mỗi vũ khí đóng gói thành một ADDON JAR riêng. Hai bên phải khớp nhau tuyệt đối qua hợp đồng này (tên package, tên class, chữ ký hàm, ý nghĩa hook, định dạng addon). Hợp đồng này ĐƯỢC ƯU TIÊN hơn mọi mô tả khác nếu có chỗ mâu thuẫn.

Mục tiêu: thêm vũ khí mới KHÔNG phải build lại jar plugin chính và KHÔNG phải restart server. Chỉ cần thả addon jar vào thư mục `plugins/UltimateWeapons/addons/` rồi gõ `/uw reload`.

### A. Cấu trúc package (cố định)
    com.example.ultimateweapons                UltimateWeaponsPlugin
    com.example.ultimateweapons.weapon         UltimateWeapon, WeaponDefinition, BehaviorRegistry, WeaponRegistry, ItemKeys
    com.example.ultimateweapons.weapons        (package chứa các vũ khí cụ thể; trong jar lõi để trống, vũ khí nằm trong addon jar cũng dùng package này)
    com.example.ultimateweapons.manager        CooldownManager, ChargeManager
    com.example.ultimateweapons.util           DamageUtil, ExplosionUtil, RayUtil, ParticleUtil
    com.example.ultimateweapons.listener       WeaponListener
    com.example.ultimateweapons.command        UwCommand
    com.example.ultimateweapons.addon          AddonManager (NỘI BỘ LÕI, addon không được dùng)

### B. Chữ ký (chỉ liệt kê API công khai; thân hàm do lõi triển khai)

```java
// ===== com.example.ultimateweapons.UltimateWeaponsPlugin =====
public class UltimateWeaponsPlugin extends JavaPlugin {
    public static final int ADDON_API_VERSION = 1;     // phiên bản API dành cho addon
    public WeaponRegistry getWeaponRegistry();
    public CooldownManager getCooldownManager();
    public ChargeManager getChargeManager();
    public ItemKeys getItemKeys();
    public AddonManager getAddonManager();             // nội bộ lõi
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

    public static void register(String type, Factory factory);   // chỉ cho vũ khí BUILT-IN nằm trong jar lõi

    // --- NỘI BỘ LÕI (AddonManager dùng, addon KHÔNG gọi) ---
    public static void registerFromAddon(String addonName, String type, Factory factory);
    public static void unregisterAddon(String addonName);

    // Được gọi trong onEnable. NƠI đăng ký vũ khí built-in. Vũ khí mới KHÔNG đăng ký ở đây mà đóng gói thành addon.
    static void registerBuiltIns() {
        // === ĐĂNG KÝ VŨ KHÍ BUILT-IN: thêm 1 dòng ngay dưới dòng này ===
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
- `onPlayerCleanup`: lõi gọi trên MỌI instance vũ khí đã đăng ký khi một người chơi thoát, chết, đổi world; và gọi cho mọi người chơi online khi `/uw reload`, khi gỡ addon, và khi onDisable. Vì thế phải chịu được việc bị gọi dù người đó không dùng vũ khí này (khi đó không làm gì). Đây là nơi duy nhất dọn task, map, bossbar, trạng thái bay, entity tự sinh...
- `/uw reload` tạo INSTANCE vũ khí mới và bỏ instance cũ (sau khi đã gọi `onPlayerCleanup` trên instance cũ). Do đó vũ khí được phép giữ state theo người chơi trong map của instance, nhưng không được giữ chỉ số cấu hình trong field.
- Lõi KHÔNG tự áp dụng cooldown: vũ khí tự gọi `CooldownManager` nếu cần (đọc `cooldown-seconds` qua `def.getDouble`).
- `DamageUtil.hit` và `ExplosionUtil.explode` tự đọc các khóa `self-damage`, `ignore-iframes`, `ignore-armor` trong yml của vũ khí; vũ khí không cần xử lý lại. Sát thương luôn được tính cho `attacker`/`source`.
- Item vũ khí đã nằm trong túi người chơi mà vũ khí/addon của nó không còn tồn tại (addon bị xóa) thì lõi chỉ bỏ qua item đó, không ném lỗi.

### D. Quy ước yml của vũ khí
- Mỗi vũ khí có một file `<id>.yml`. Với vũ khí trong addon, file nằm trong jar tại `weapons/<id>.yml`; lõi tự copy mọi `weapons/*.yml` trong addon jar ra `plugins/UltimateWeapons/weapons/` nếu file đó chưa tồn tại (không đè), rồi nạp mọi `*.yml` trong thư mục đó.
- Khóa bắt buộc: `id`, `type`, `display-name` (MiniMessage), `description` (MiniMessage), `material`.
- Tùy chọn chung (mọi vũ khí có thể dùng): `cooldown-seconds`, `self-damage` (mặc định false), `ignore-iframes` (mặc định false), `ignore-armor` (mặc định false), `custom-model-data`, `unbreakable`, `lore` (danh sách MiniMessage).
- Mọi chỉ số riêng của vũ khí (sát thương, bán kính, số lượng, màu hex `#RRGGBB`...) nằm trong yml cùng cấp với các khóa trên (hoặc nhóm gọn trong một section), đọc bằng `def.getXxx("khóa", mặcĐịnh)`.
- Một `type` (class Java) có thể dùng cho nhiều yml khác nhau; id là duy nhất theo từng yml.

### E. Hệ thống ADDON (nạp lúc chạy, không cần build lại lõi, không cần restart)

**E1. Cấu trúc một addon jar**
    <id>.jar
    ├─ addon.yml                                          (bắt buộc, nằm ở gốc jar)
    ├─ com/example/ultimateweapons/weapons/<TenClass>.class
    └─ weapons/<id>.yml                                   (một hoặc nhiều yml của vũ khí)

**E2. `addon.yml`**
    name: storm_repeater              # tên addon, duy nhất, chỉ gồm a-z 0-9 _ -
    version: 1.0.0
    api-version: 1                    # phải bằng UltimateWeaponsPlugin.ADDON_API_VERSION, khác thì lõi bỏ qua addon và báo lỗi
    weapons:                          # mỗi phần tử khai báo một "type" (class hành vi)
      - type: crossbow_repeater
        class: com.example.ultimateweapons.weapons.RepeaterCrossbow

Class khai báo phải kế thừa `UltimateWeapon` và có constructor công khai `(UltimateWeaponsPlugin plugin, WeaponDefinition def)`.

**E3. Cách lõi nạp addon (AddonManager)**
- Quét `plugins/UltimateWeapons/addons/*.jar` lúc onEnable và lúc `/uw reload`.
- KHÔNG nạp trực tiếp từ file trong `addons/` (Windows sẽ khóa file khiến người dùng không thay jar được). Mỗi lần nạp, copy jar sang thư mục cache `plugins/UltimateWeapons/.addon-cache/` với tên duy nhất rồi nạp từ bản copy.
- Mỗi addon dùng MỘT `URLClassLoader` riêng, parent là classloader của plugin lõi (nên addon thấy được toàn bộ API của lõi và Paper). Đọc `addon.yml`, kiểm tra `api-version`, nạp class, kiểm tra kế thừa `UltimateWeapon` và constructor, rồi đăng ký bằng `BehaviorRegistry.registerFromAddon(name, type, factory)`.
- Lỗi của một addon (thiếu addon.yml, sai api-version, class không tìm thấy, sai constructor, trùng type với addon khác, yml hỏng) chỉ làm bỏ qua addon/vũ khí đó, ghi rõ tên addon và lý do vào console và vào danh sách lỗi; KHÔNG làm hỏng các addon khác hay toàn bộ quá trình reload.
- Trùng `type` giữa hai addon: addon nạp trước thắng, addon sau bị bỏ qua type đó kèm cảnh báo. Trùng `id` vũ khí giữa hai yml: yml sau bị bỏ qua kèm cảnh báo.

**E4. Quy trình `/uw reload` (thứ tự bắt buộc)**
1. Gọi `onPlayerCleanup` trên mọi vũ khí cho mọi người chơi online; hủy mọi task/charge/bossbar đang chạy.
2. Xóa toàn bộ instance trong WeaponRegistry; `unregisterAddon` cho mọi addon; đóng (`close()`) mọi addon classloader; xóa cache cũ.
3. Reload `config.yml`.
4. `BehaviorRegistry.registerBuiltIns()`, rồi nạp lại toàn bộ addon (E3), rồi quét `weapons/*.yml` dựng lại instance.
5. Báo số vũ khí đã load, số addon đã nạp, và các lỗi (nếu có).
Cùng quy trình này (bước 1-2) chạy khi onDisable.

**E5. Lệnh liên quan**
- `/uw addons`: liệt kê addon đã nạp (tên, version, các type, tên jar) và các lỗi của lần nạp gần nhất.
- `/uw list`: cho mỗi vũ khí, ngoài `id` và `Tên | mô tả`, ghi nguồn (built-in hoặc tên addon).

**E6. Quy tắc BẮT BUỘC cho code trong addon** (vì addon bị gỡ và nạp lại khi reload)
- Không dùng biến `static` giữ state có thể thay đổi (map, danh sách, task...). State theo người chơi lưu trong field của instance vũ khí và dọn trong `onPlayerCleanup`.
- Không tự đăng ký Bukkit `Listener`, không tự đăng ký command. Mọi tương tác đi qua các hook. Nếu cần sự kiện mà hook chưa có thì nêu đề xuất thay đổi hợp đồng, đừng hack.
- Mọi task `BukkitRunnable` do vũ khí tạo phải được lưu lại và `cancel()` trong `onPlayerCleanup`; mọi entity vũ khí tự sinh (TNT, trident, armor stand...) phải được xóa trong `onPlayerCleanup`; mọi block vũ khí đã đổi tạm thời (băng, v.v.) phải được trả lại trong `onPlayerCleanup`.
- Không phụ thuộc vào class của addon khác. Chỉ dùng API trong hợp đồng này và Paper API 1.21.11.
- Class vũ khí nằm trong package `com.example.ultimateweapons.weapons`, tên class phải duy nhất so với các addon khác.

### F. Dự án build addon (addons-workspace)
Là một dự án Gradle RIÊNG (không phải dự án plugin lõi), dùng để build mỗi vũ khí thành một jar nhỏ:
    addons-workspace/
    ├─ settings.gradle.kts        tự include mọi thư mục con của `addons/` có chứa `src/` (thêm vũ khí mới không phải sửa file này)
    ├─ build.gradle.kts           cấu hình chung cho mọi module addon + task `collectAddons`
    ├─ gradle.properties
    ├─ libs/UltimateWeapons.jar   jar plugin lõi đã build (người dùng tự copy vào), dùng compileOnly
    └─ addons/
       └─ <id>/                   MỘT module = MỘT vũ khí (người dùng chỉ cần bỏ thư mục này vào)
          └─ src/main/
             ├─ java/com/example/ultimateweapons/weapons/<TenClass>.java
             └─ resources/
                ├─ addon.yml
                └─ weapons/<id>.yml
- Module KHÔNG có `build.gradle.kts` riêng; mọi cấu hình (Java 21, compileOnly paper-api 1.21.11 và libs/UltimateWeapons.jar, tên jar = tên module) nằm ở `build.gradle.kts` gốc.
- `./gradlew collectAddons` build tất cả module và copy các jar vào `build/addons/`; `./gradlew :addons:<id>:jar` chỉ build một module (jar ở `addons/<id>/build/libs/<id>.jar`).
- Người dùng copy jar vào `plugins/UltimateWeapons/addons/` rồi gõ `/uw reload`.

==================================================
## QUY TẮC BẮT BUỘC
==================================================
1. Tạo ĐÚNG 1 class mới kế thừa `UltimateWeapon`, đặt trong package `com.example.ultimateweapons.weapons`, constructor công khai `(UltimateWeaponsPlugin plugin, WeaponDefinition def)` gọi `super(plugin, def)`. Chỉ override các hook cần thiết.
2. Tạo file yml của vũ khí tại `src/main/resources/weapons/<id>.yml` theo đúng quy ước yml trong hợp đồng (khóa bắt buộc + tùy chọn chung), có comment tiếng Việt giải thích từng khóa. KHÔNG hard-code chỉ số, màu, cooldown, bán kính, sát thương, số lượng... trong code. Mọi giá trị chỉnh được phải nằm trong yml, đọc qua `def.getDouble/getInt/getBoolean/getString/getStringList` TẠI THỜI ĐIỂM DÙNG (không cache vào field) và luôn có giá trị mặc định hợp lý.
3. Tạo file `src/main/resources/addon.yml` theo đúng mục E2 (`name` = id vũ khí, `version: 1.0.0`, `api-version: 1`, khai báo `type` và `class` đầy đủ tên package). KHÔNG có "dòng đăng ký" nào: addon tự khai báo trong addon.yml. KHÔNG viết hay sửa bất kỳ file nào của lõi.
4. Tuân thủ nghiêm mục E6 vì addon bị gỡ và nạp lại khi `/uw reload`: không `static` giữ state có thể thay đổi, không tự đăng ký Listener/command, mọi task phải được lưu và hủy trong `onPlayerCleanup`, mọi entity tự sinh phải được xóa trong `onPlayerCleanup`, mọi block đổi tạm thời phải được trả lại trong `onPlayerCleanup`.
5. Nếu bạn cần thứ KHÔNG có trong hợp đồng:
   - Ưu tiên tự cài đặt như một hàm private trong chính class vũ khí và ghi chú rõ.
   - Nếu thật sự cần một hook hoặc tiện ích mới của lõi, ĐỪNG hack. Hãy viết mục riêng "ĐỀ XUẤT THAY ĐỔI HỢP ĐỒNG" (chữ ký tối thiểu + lý do), và cố viết vũ khí sao cho vẫn compile được khi chưa có đề xuất đó (giảm tính năng thay vì làm hỏng).
6. Nhận diện item chỉ bằng PDC `weapon_id` qua `plugin.getItemKeys()`, không dựa vào tên/lore. Không lưu chỉ số vào item.
7. MỌI sát thương phải đi qua `DamageUtil.hit(...)`, MỌI vụ nổ qua `ExplosionUtil.explode(...)`, để sát thương luôn được tính cho người dùng (kill credit, rơi đồ, XP) và tôn trọng `self-damage`, `ignore-iframes`, `ignore-armor` trong yml. Entity vũ khí tự sinh thì gắn nguồn: TNT `setSource(player)`, sét `setCausingPlayer(player)`, projectile `setShooter(player)`, và gắn tag bằng `plugin.getItemKeys().tagProjectile(...)` khi cần để `onProjectileHit` nhận ra.
8. Dùng lại tiện ích trong hợp đồng (CooldownManager, ChargeManager, RayUtil, ParticleUtil...), không viết lại bản trùng chức năng. Màu particle đọc từ yml dạng hex qua `ParticleUtil.parseHex`.
9. Nếu vũ khí bật thứ gì cho người chơi (allowFlight, effect, attribute...) thì phải trả lại đúng khi dừng, và CHỈ trả lại nếu chính vũ khí đã bật nó.
10. Chỉ dùng Paper API 1.21.11: không NMS, không reflection, không API deprecated, không API riêng của Folia. Hiệu ứng kéo dài dùng BukkitRunnable/scheduler và phải hủy được; không chặn main thread bằng vòng lặp dài.
11. Hiệu năng: tránh quét entity/block quá lớn mỗi tick; giới hạn số entity/particle sinh ra bằng giá trị cấu hình (có mặc định an toàn). `onTick` chạy mỗi tick nên phải nhẹ.
12. Không để NPE khi item không hợp lệ, người chơi offline, world unload.
13. Comment bằng tiếng Việt, tên class/biến bằng tiếng Anh.

==================================================
## MÔ TẢ VŨ KHÍ CẦN TẠO
==================================================
Mô tả vũ khí nằm NGAY BÊN DƯỚI prompt này (do tôi gửi kèm, có thể viết tự do hoặc theo phiếu: tên, id, vật phẩm, cách kích hoạt, hành vi, sát thương, hiệu ứng, chỉ số chỉnh được, ghi chú).
- Nếu mô tả thiếu thông tin (id, type, giá trị mặc định, màu, thời gian hồi...), hãy tự chọn giá trị hợp lý, đặt vào yml và nêu rõ giả định ở phần tóm tắt; đừng hỏi lại nhiều.
- Nếu tôi không đặt id/type, hãy tự đặt dạng snake_case dễ nhớ (id là tên vũ khí, type là tên hành vi).
- Chỉ tạo ĐÚNG MỘT vũ khí cho mỗi lần trả lời.

==================================================
## ĐỊNH DẠNG TRẢ LỜI
==================================================
1. Tóm tắt NGẮN cách bạn hiểu yêu cầu và các giả định (nếu mơ hồ thì nêu giả định, đừng hỏi lại nhiều).
2. Cây thư mục của module addon, đúng dạng:
       addons/<id>/src/main/java/com/example/ultimateweapons/weapons/<TenClass>.java
       addons/<id>/src/main/resources/addon.yml
       addons/<id>/src/main/resources/weapons/<id>.yml
3. Nội dung ĐẦY ĐỦ của từng file trong 3 file trên (ghi rõ đường dẫn; class có đủ package và import).
4. (Chỉ khi cần) mục "ĐỀ XUẤT THAY ĐỔI HỢP ĐỒNG" theo quy tắc 5.
5. Các bước test nhanh trên server (`/uw reload`, `/uw addons`, `/uw give <id>`, thao tác kiểm tra từng chức năng) và danh sách những điểm bạn không chắc về API Paper cần tôi kiểm tra.

==================================================
## MÔ TẢ VŨ KHÍ (do tôi gửi kèm, nằm ngay dưới dòng này)
==================================================

