package com.example.ultimateweapons.weapon;

import com.example.ultimateweapons.UltimateWeaponsPlugin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Ánh xạ type -> factory tạo vũ khí, kèm chủ sở hữu của từng type (built-in hoặc tên addon).
 * Một type có thể dùng cho nhiều file yml khác nhau.
 *
 * Vũ khí MỚI không đăng ký ở đây mà đóng gói thành addon jar (AddonManager gọi registerFromAddon).
 * registerBuiltIns() chỉ dành cho vũ khí built-in nằm trong jar lõi.
 */
public final class BehaviorRegistry {

    @FunctionalInterface
    public interface Factory {
        UltimateWeapon create(UltimateWeaponsPlugin plugin, WeaponDefinition def);
    }

    /** Nhãn nguồn hiển thị cho vũ khí built-in. */
    public static final String BUILTIN_SOURCE = "built-in";

    /** owner == null nghĩa là built-in. */
    private record Registration(Factory factory, String owner) {
    }

    private static final Map<String, Registration> REGISTRATIONS = new HashMap<>();

    private BehaviorRegistry() {
    }

    /** Đăng ký type built-in (chỉ cho vũ khí nằm trong jar lõi). Type không phân biệt hoa thường. */
    public static void register(String type, Factory factory) {
        REGISTRATIONS.put(type.toLowerCase(Locale.ROOT), new Registration(factory, null));
    }

    // --- NỘI BỘ LÕI (AddonManager dùng, addon KHÔNG gọi) ---

    /** Đăng ký type của một addon. Nếu type đã tồn tại thì giữ nguyên bản cũ (người đăng ký trước thắng). */
    public static void registerFromAddon(String addonName, String type, Factory factory) {
        REGISTRATIONS.putIfAbsent(type.toLowerCase(Locale.ROOT), new Registration(factory, addonName));
    }

    /** Gỡ mọi type thuộc về addon đó. */
    public static void unregisterAddon(String addonName) {
        REGISTRATIONS.values().removeIf(reg -> addonName != null && addonName.equals(reg.owner()));
    }

    // --- Truy vấn ---

    /** Lấy factory theo type, null nếu chưa đăng ký. */
    public static Factory get(String type) {
        if (type == null) {
            return null;
        }
        Registration reg = REGISTRATIONS.get(type.toLowerCase(Locale.ROOT));
        return reg == null ? null : reg.factory();
    }

    public static boolean has(String type) {
        return type != null && REGISTRATIONS.containsKey(type.toLowerCase(Locale.ROOT));
    }

    /** Nguồn của type: tên addon hoặc "built-in". null nếu type chưa đăng ký. */
    public static String sourceOf(String type) {
        if (type == null) {
            return null;
        }
        Registration reg = REGISTRATIONS.get(type.toLowerCase(Locale.ROOT));
        if (reg == null) {
            return null;
        }
        return reg.owner() == null ? BUILTIN_SOURCE : reg.owner();
    }

    /** Danh sách type đã đăng ký (dùng cho thông báo lỗi). */
    public static Set<String> types() {
        return new TreeSet<>(REGISTRATIONS.keySet());
    }

    /** Được plugin gọi lúc onEnable và /uw reload: xóa sạch rồi đăng ký lại built-in. */
    public static void bootstrap() {
        REGISTRATIONS.clear();
        registerBuiltIns();
    }

    // Được gọi trong onEnable (qua bootstrap). NƠI đăng ký vũ khí built-in.
    // Vũ khí mới KHÔNG đăng ký ở đây mà đóng gói thành addon.
    static void registerBuiltIns() {
        // === ĐĂNG KÝ VŨ KHÍ BUILT-IN: thêm 1 dòng ngay dưới dòng này ===
    }
}
