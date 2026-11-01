# addons-workspace

Dự án Gradle **riêng** để build mỗi vũ khí thành một addon jar nhỏ cho plugin UltimateWeapons.
Thêm vũ khí mới không cần build lại plugin chính và không cần restart server.

## Cấu trúc

```
addons-workspace/
├─ settings.gradle.kts     tự include mọi thư mục con của addons/ có chứa src/
├─ build.gradle.kts        cấu hình chung (Java 21, compileOnly) + task collectAddons
├─ gradle.properties
├─ libs/UltimateWeapons.jar   jar plugin lõi đã build (bạn tự copy vào)
└─ addons/
   └─ <id>/                   MỘT module = MỘT vũ khí
      └─ src/main/
         ├─ java/com/example/ultimateweapons/weapons/<TenClass>.java
         └─ resources/
            ├─ addon.yml
            └─ weapons/<id>.yml
```

Module không có `build.gradle.kts` riêng. Chỉ cần bỏ thư mục `addons/<id>/` vào là xong.

## Chuẩn bị (một lần)

1. Build plugin lõi: `./gradlew build` trong dự án UltimateWeapons.
2. Copy `build/libs/UltimateWeapons-<version>.jar` vào `libs/UltimateWeapons.jar` (đúng tên này).
3. Cần JDK 21 và Gradle 8.x (hoặc tạo wrapper một lần bằng `gradle wrapper`).

## Build

```
./gradlew collectAddons          # build tất cả addon, jar nằm ở build/addons/
./gradlew :addons:hello_stick:jar   # chỉ build một module, jar ở addons/hello_stick/build/libs/hello_stick.jar
```

## Dùng

1. Copy jar vào `plugins/UltimateWeapons/addons/` của server.
2. Gõ `/uw reload`, rồi `/uw addons` để kiểm tra, `/uw give <id>` để nhận vũ khí.

Thay jar mới: copy đè vào `addons/` rồi `/uw reload` (lõi nạp từ bản copy trong `.addon-cache`, không khóa file).

## Thêm một vũ khí mới

1. Tạo thư mục `addons/<id>/` với cấu trúc như trên.
2. `addon.yml`: `name` (a-z 0-9 _ -), `version`, `api-version: 1`, danh sách `weapons` gồm `type` và `class`.
3. Class vũ khí kế thừa `UltimateWeapon`, có constructor công khai `(UltimateWeaponsPlugin plugin, WeaponDefinition def)`.
4. `weapons/<id>.yml` với các khóa bắt buộc `id`, `type`, `display-name`, `description`, `material`.

Lõi tự copy `weapons/*.yml` trong addon ra `plugins/UltimateWeapons/weapons/` nếu file đó chưa tồn tại (không đè chỉnh sửa của bạn).

## Quy tắc bắt buộc cho code addon

- Không dùng biến `static` giữ state thay đổi được. State theo người chơi lưu trong field của instance và dọn trong `onPlayerCleanup`.
- Không tự đăng ký `Listener` hay command. Mọi tương tác đi qua các hook. Cần sự kiện mà hook chưa có thì đề xuất thay đổi API.
- Mọi task phải được lưu và `cancel()` trong `onPlayerCleanup`; entity tự sinh phải xóa, block đổi tạm phải trả lại trong `onPlayerCleanup`.
- Không phụ thuộc class của addon khác. Chỉ dùng API của lõi và Paper API 1.21.11.
- Tên class phải duy nhất so với các addon khác.
- Đọc chỉ số bằng `def.getDouble("khóa", mặcĐịnh)` tại thời điểm dùng, không cache vào field.

## Addon mẫu

`addons/hello_stick`: cây gậy (STICK), click phải gửi tin nhắn vào chat và vẽ tia hạt. Dùng để kiểm tra quy trình.
