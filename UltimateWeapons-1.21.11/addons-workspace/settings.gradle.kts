rootProject.name = "addons-workspace"

// Tự include mọi thư mục con của addons/ có chứa src/.
// Thêm vũ khí mới chỉ cần bỏ thư mục <id>/ vào addons/, KHÔNG phải sửa file này.
val addonsDir = file("addons")
if (addonsDir.isDirectory) {
    addonsDir.listFiles()
        ?.filter { it.isDirectory && File(it, "src").isDirectory }
        ?.sortedBy { it.name }
        ?.forEach { dir ->
            include(":addons:${dir.name}")
            project(":addons:${dir.name}").projectDir = dir
        }
}
