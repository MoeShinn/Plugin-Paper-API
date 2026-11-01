########################################################################
# GHI CHÚ CHO BẠN (xóa phần này khi dán prompt)
########################################################################
- Prompt này ĐỘC LẬP: dán vào một cuộc trò chuyện mới với Claude là dùng được ngay. KHÔNG cần đính kèm file nào và KHÔNG cần plugin lõi đã làm xong. Bạn có thể chạy song song với Prompt 1.
- Mọi thứ Claude cần biết về plugin nằm trong phần "HỢP ĐỒNG API" (cùng một đoạn y hệt có trong Prompt 1). Hai Claude khác nhau cùng bám hợp đồng nên code sẽ khớp khi ghép lại.
- Mỗi cuộc trò chuyện làm 1 vũ khí. Điền phần "VŨ KHÍ CẦN TẠO" ở cuối prompt, hoặc copy phiếu điền sẵn trong PHỤ LỤC cuối file.
- Khi ghép vào dự án: copy class vũ khí vào package `com.example.ultimateweapons.weapons`, yml vào `src/main/resources/weapons/`, dán dòng đăng ký xuống dưới dòng marker trong `BehaviorRegistry.registerBuiltIns()`.
- Nếu build lỗi vì lệch chữ ký, gửi log lỗi cho Claude tương ứng (lõi hoặc vũ khí) kèm file liên quan để sửa cho khớp hợp đồng.
- Hợp đồng ở Prompt 1 và Prompt 2 phải luôn GIỐNG NHAU. Nếu sau này bạn sửa hợp đồng ở file này thì cũng phải sửa ở file kia.

########################################################################
# PROMPT (bắt đầu dán từ dòng dưới)
########################################################################

Hãy viết MỘT vũ khí tối thượng mới cho plugin Minecraft "UltimateWeapons" (Paper/Leaf 1.21.11, Java 21, Gradle).

Bạn KHÔNG có mã nguồn của plugin. Toàn bộ API bạn được phép dùng nằm trong HỢP ĐỒNG API bên dưới. Lõi plugin đang được người khác viết riêng, song song, theo đúng hợp đồng này, và code của bạn sẽ được ghép vào sau. Vì vậy:
- Chỉ gọi những class/hàm có trong hợp đồng, đúng package và đúng chữ ký. KHÔNG bịa thêm API của plugin.
- KHÔNG viết lại hay giả lập các class của lõi (UltimateWeapon, DamageUtil...). Chỉ import chúng.
- Bạn không thể chạy hay compile code, nên hãy tự rà soát kỹ import, tên API Paper và kiểu dữ liệu trước khi trả lời.

Bạn là BÊN VIẾT VŨ KHÍ: chỉ được dùng đúng những gì có trong hợp đồng dưới đây. Bên kia sẽ triển khai lõi y hệt hợp đồng này.

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
## QUY TẮC BẮT BUỘC
==================================================
1. Tạo ĐÚNG 1 class mới kế thừa `UltimateWeapon`, đặt trong package `com.example.ultimateweapons.weapons`, constructor `(UltimateWeaponsPlugin plugin, WeaponDefinition def)` gọi `super(plugin, def)`. Chỉ override các hook cần thiết.
2. Tạo 1 file yml mới tại `src/main/resources/weapons/<id>.yml` theo đúng quy ước yml trong hợp đồng (khóa bắt buộc + tùy chọn chung), có comment tiếng Việt giải thích từng khóa. KHÔNG hard-code chỉ số, màu, cooldown, bán kính, sát thương, số lượng... trong code. Mọi giá trị chỉnh được phải nằm trong yml, đọc qua `def.getDouble/getInt/getBoolean/getString/getStringList` TẠI THỜI ĐIỂM DÙNG (không cache vào field) và luôn có giá trị mặc định hợp lý.
3. Đăng ký bằng ĐÚNG 1 dòng `register("<type>", <TenClass>::new);` để người dùng dán xuống dưới marker trong `BehaviorRegistry.registerBuiltIns()`. KHÔNG viết hay sửa bất kỳ file nào khác của lõi (listener, registry, util...).
4. Nếu bạn cần thứ KHÔNG có trong hợp đồng:
   - Ưu tiên tự cài đặt như một hàm private trong chính class vũ khí và ghi chú rõ.
   - Nếu thật sự cần một hook hoặc tiện ích mới của lõi, ĐỪNG hack. Hãy viết mục riêng "ĐỀ XUẤT THAY ĐỔI HỢP ĐỒNG" (chữ ký tối thiểu + lý do), và cố viết vũ khí sao cho vẫn compile được khi chưa có đề xuất đó (giảm tính năng thay vì làm hỏng).
5. Nhận diện item chỉ bằng PDC `weapon_id` qua `plugin.getItemKeys()`, không dựa vào tên/lore. Không lưu chỉ số vào item.
6. MỌI sát thương phải đi qua `DamageUtil.hit(...)`, MỌI vụ nổ qua `ExplosionUtil.explode(...)`, để sát thương luôn được tính cho người dùng (kill credit, rơi đồ, XP) và tôn trọng `self-damage`, `ignore-iframes`, `ignore-armor` trong yml. Entity vũ khí tự sinh thì gắn nguồn: TNT `setSource(player)`, sét `setCausingPlayer(player)`, projectile `setShooter(player)`, và gắn tag bằng `plugin.getItemKeys().tagProjectile(...)` khi cần để `onProjectileHit` nhận ra.
7. Dùng lại tiện ích trong hợp đồng (CooldownManager, ChargeManager, RayUtil, ParticleUtil...), không viết lại bản trùng chức năng. Màu particle đọc từ yml dạng hex qua `ParticleUtil.parseHex`.
8. Mọi state theo người chơi (task, map, bossbar, trạng thái bay, effect...) phải được dọn trong `onPlayerCleanup`. Nếu vũ khí bật thứ gì cho người chơi (allowFlight, effect, attribute...) thì phải trả lại đúng khi dừng, và CHỈ trả lại nếu chính vũ khí đã bật nó.
9. Chỉ dùng Paper API 1.21.11: không NMS, không reflection, không API deprecated, không API riêng của Folia. Hiệu ứng kéo dài dùng BukkitRunnable/scheduler và phải hủy được; không chặn main thread bằng vòng lặp dài.
10. Hiệu năng: tránh quét entity/block quá lớn mỗi tick; giới hạn số entity/particle sinh ra bằng giá trị cấu hình (có mặc định an toàn). `onTick` chạy mỗi tick nên phải nhẹ.
11. Không để NPE khi item không hợp lệ, người chơi offline, world unload.
12. Comment bằng tiếng Việt, tên class/biến bằng tiếng Anh.

==================================================
## VŨ KHÍ CẦN TẠO (phiếu mô tả)
==================================================
- Tên hiển thị:
- id (snake_case):
- type (tên hành vi, snake_case):
- Vật phẩm (material):
- Cách kích hoạt (click trái/phải, shift, bắn, giữ, mặc...):
- Hiệu ứng / hành vi chi tiết:
- Sát thương / vụ nổ / thời gian hồi (nếu có):
- Hiệu ứng hình ảnh, âm thanh (màu, particle...):
- Những chỉ số muốn chỉnh được trong yml (kèm giá trị mặc định):
- Ghi chú đặc biệt (dùng 1 lần, giới hạn, ràng buộc...):

==================================================
## ĐỊNH DẠNG TRẢ LỜI
==================================================
1. Tóm tắt NGẮN cách bạn hiểu yêu cầu và các giả định (nếu mơ hồ thì nêu giả định, đừng hỏi lại nhiều).
2. Nội dung ĐẦY ĐỦ file class mới (ghi rõ đường dẫn, có đủ package và import).
3. Nội dung ĐẦY ĐỦ file yml mới (ghi rõ đường dẫn).
4. Đúng 1 dòng đăng ký để dán dưới marker trong `BehaviorRegistry.registerBuiltIns()`.
5. (Chỉ khi cần) mục "ĐỀ XUẤT THAY ĐỔI HỢP ĐỒNG" theo quy tắc 4.
6. Các bước test nhanh trên server (lệnh `/uw give <id>`, thao tác kiểm tra từng chức năng) và danh sách những điểm bạn không chắc về API Paper cần tôi kiểm tra.

########################################################################
# HẾT PROMPT
########################################################################


########################################################################
# PHỤ LỤC: PHIẾU ĐIỀN SẴN CHO 5 VŨ KHÍ ĐẦU (copy 1 phiếu, dán thay cho phần "VŨ KHÍ CẦN TẠO")
########################################################################

----- PHIẾU 1: Phong Tốc Liên Nỏ -----
- Tên hiển thị: Phong Tốc Liên Nỏ
- id: storm_repeater
- type: crossbow_repeater
- Vật phẩm: CROSSBOW
- Cách kích hoạt: click phải để bắn như nỏ thường
- Hiệu ứng / hành vi chi tiết: sau mỗi phát bắn, nỏ tự nạp lại gần như ngay lập tức (độ trễ rất ngắn) để có thể bắn liên tục. Mỗi lần nạp trừ 1 mũi tên trong inventory của người chơi; ưu tiên chọn mũi tên theo đúng thứ tự như nỏ thường (tay phụ, rồi hotbar, rồi phần còn lại của inventory), hỗ trợ mũi tên thường, spectral và tipped. Không có mũi tên thì không nạp được. Sát thương và hiệu ứng mũi tên giữ như vanilla.
- Sát thương / thời gian hồi: sát thương mũi tên vanilla, không cooldown riêng
- Hiệu ứng hình ảnh, âm thanh: không đặc biệt (có thể thêm âm nạp nhanh nhẹ, chỉnh được)
- Chỉ số chỉnh được: reload-delay-ticks (mặc định 4), arrow-speed (3.15), consume-arrows (true; nếu false thì không trừ mũi tên)
- Ghi chú đặc biệt: mũi tên bắn ra phải được gắn setShooter(player)

----- PHIẾU 2: Diệt Thế Nỏ -----
- Tên hiển thị: Diệt Thế Nỏ
- id: doomsday_crossbow
- type: crossbow_doomsday
- Vật phẩm: CROSSBOW
- Cách kích hoạt: click phải để bắn
- Hiệu ứng / hành vi chi tiết: nỏ luôn tự nạp sẵn mũi tên, không cần và không tiêu mũi tên trong inventory. Mũi tên chạm bất kì thứ gì (block hoặc entity) thì lập tức tạo nhiều vụ nổ liên tiếp tại điểm chạm, rồi xóa mũi tên.
- Sát thương / vụ nổ: explosion-count 3, explosion-power 10.0, mỗi vụ nổ cách nhau vài tick
- Hiệu ứng hình ảnh, âm thanh: mặc định như vụ nổ vanilla, có thể thêm particle dấu vết cho mũi tên
- Chỉ số chỉnh được: explosion-count (3), explosion-power (10.0), explosion-delay-ticks (2), break-blocks (true), fire (false), self-damage (false), arrow-speed (3.15)
- Ghi chú đặc biệt: vụ nổ công suất 10 rất nặng, nên có giới hạn trần (max-explosion-power) trong config; sát thương nổ phải tính cho người bắn

----- PHIẾU 3: Trảm Thiên Kiếm -----
- Tên hiển thị: Trảm Thiên Kiếm
- id: heaven_slayer
- type: sword_heaven_slayer
- Vật phẩm: DIAMOND_SWORD
- Cách kích hoạt: giữ shift để sạc năng lượng, click trái để chém
- Hiệu ứng / hành vi chi tiết: khi giữ shift hiện thanh sạc (BossBar hoặc ActionBar) tối thiểu 3 giây, tối đa 100 giây. Nhả shift trước khi đủ tối thiểu thì hủy sạc. Sau khi sạc đủ tối thiểu, click trái tạo một đường chém cao 5 block, bay từ trước mặt người chơi ra phía trước 64 block theo hướng nhìn, gây sát thương cho mọi entity trên đường chém (mỗi mục tiêu chỉ trúng 1 lần mỗi nhát). Sát thương = sát thương cơ bản (7) x số giây đã sạc (ví dụ sạc 3 giây thì x3), có giới hạn hệ số tối đa chỉnh được. Dùng xong thì reset sạc.
- Sát thương / thời gian hồi: base-damage 7, nhân theo số giây sạc, cooldown sau khi chém chỉnh được
- Hiệu ứng hình ảnh, âm thanh: đường chém vẽ bằng particle dust có màu đổi được qua yml
- Chỉ số chỉnh được: min-charge-seconds (3), max-charge-seconds (100), base-damage (7.0), max-damage-multiplier (100), slash-height (5.0), slash-range (64.0), slash-color ("#66ccff"), slash-particle-size, cooldown-seconds
- Ghi chú đặc biệt: sát thương phải đi qua DamageUtil.hit; thanh sạc phải tự dọn khi người chơi đổi slot, thoát, chết

----- PHIẾU 4: Hạt Nhân Cần -----
- Tên hiển thị: Hạt Nhân Cần
- id: nuke_rod
- type: rod_nuke
- Vật phẩm: FISHING_ROD
- Cách kích hoạt: click phải, dùng 1 lần
- Hiệu ứng / hành vi chi tiết: khi click phải thì hủy thao tác thả phao (xử lý cả PlayerInteractEvent lẫn PlayerFishEvent), trừ vật phẩm khỏi tay, rồi tạo nhiều quả TNTPrimed tại chỗ người chơi đứng. Mỗi quả được đẩy bằng velocity ra ngoài từ tâm theo các góc đều nhau để xếp thành vòng tròn (dùng velocity chứ không teleport, để block chặn được TNT một cách tự nhiên). Sau fuse thì nổ.
- Sát thương / vụ nổ: TNT vanilla với yield chỉnh được
- Hiệu ứng hình ảnh, âm thanh: không đặc biệt
- Chỉ số chỉnh được: tnt-count (32), push-speed (0.9), vertical-boost (0.15), fuse-ticks (80), yield (4.0), break-blocks (true), self-damage (false), max-tnt-per-use (giới hạn trần để tránh lag)
- Ghi chú đặc biệt: TNT phải setSource(player) để sát thương tính cho người dùng; nhớ tính toán sao cho người dùng không dễ tự nổ mình khi self-damage = false

----- PHIẾU 5: Thiên Sứ Dực -----
- Tên hiển thị: Thiên Sứ Dực
- id: angel_wings
- type: wings_angel
- Vật phẩm: ELYTRA (unbreakable), mặc ở slot ngực
- Cách kích hoạt: mặc vào là có hiệu lực
- Hiệu ứng / hành vi chi tiết: (1) khi mặc, người chơi bay được như gamemode creative (setAllowFlight(true)); khi tháo ra, chết, thoát, đổi gamemode hoặc reload thì tắt, và CHỈ tắt nếu chính plugin đã bật (lưu bằng Set<UUID>). (2) khi đang lướt elytra (isGliding): mỗi tick đẩy velocity theo hướng nhìn để bay mà không cần pháo hoa; sneak để phanh. (3) khi đứng yên hoặc bay kiểu creative (không lướt): vẽ đôi cánh trắng bằng particle ở phía sau lưng, xoay theo body yaw của người chơi, thấy là biết có cánh. (4) khi đang lướt elytra: tắt cánh particle, thay bằng vệt dust trắng để lại phía sau đường bay.
- Sát thương / thời gian hồi: không có sát thương
- Hiệu ứng hình ảnh, âm thanh: màu cánh và màu vệt bay đổi được qua yml
- Chỉ số chỉnh được: creative-flight (true), glide-speed (1.2), brake-factor (0.5), wing-color ("#ffffff"), trail-color ("#ffffff"), wing-particle-size, particle-interval-ticks (3), wing-span, wing-height
- Ghi chú đặc biệt: cánh elytra vanilla vẫn hiện trên lưng (chỉ ẩn được bằng resource pack), hãy ghi chú điều này; dọn sạch task và trạng thái bay trong onPlayerCleanup
