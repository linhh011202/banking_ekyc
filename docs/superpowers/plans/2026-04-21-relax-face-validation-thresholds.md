# Relax Face Validation Thresholds Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nới lỏng các ngưỡng nhận diện khuôn mặt trong eKYC để user dễ chụp hơn mà không ảnh hưởng bảo mật.

**Architecture:** Chỉ thay đổi các hằng số `private const val` trong `object FaceValidator` tại `FaceValidation.kt`. Không thay đổi logic, không thêm file. Tất cả validation path giữ nguyên — chỉ các ngưỡng số thay đổi.

**Tech Stack:** Kotlin, ML Kit Face Detection, Android CameraX

---

### Task 1: Cập nhật các threshold trong FaceValidator

**Files:**
- Modify: `app/src/main/java/com/linh/banking_ekyc/ekyc/FaceValidation.kt:31-57`

- [ ] **Step 1: Mở file và xác định vùng cần thay đổi**

  Kiểm tra phần `// ── Thresholds ──` từ dòng 31–57 trong `FaceValidation.kt`. Đây là toàn bộ vùng sẽ chỉnh.

- [ ] **Step 2: Thay thế toàn bộ khối threshold**

  Thay thế đoạn từ dòng 31 đến 57 bằng:

  ```kotlin
  // ── Thresholds ──────────────────────────────────────────────────────
  private const val EYE_OPEN_MIN_PROB = 0.25f
  private const val MIN_FACE_RATIO = 0.25f
  private const val TOO_FAR_FACE_RATIO = 0.15f

  // Bounding box aspect ratio (width / height). Normal frontal face ≈ 0.65 – 1.15
  private const val BB_ASPECT_RATIO_MIN = 0.50f
  private const val BB_ASPECT_RATIO_MAX = 1.40f

  // Contour: ML Kit returns ~36 points for FACE contour when fully visible
  private const val MIN_FACE_CONTOUR_POINTS = 15

  // Bounding box stability: max allowed change ratio between frames
  private const val BB_MAX_CHANGE_RATIO = 0.35f
  private const val BB_MAX_CENTER_SHIFT_RATIO = 0.30f

  // Euler angle thresholds
  private const val EULER_Y_CENTER_MAX = 18f
  private const val EULER_Y_LEFT_MIN = 15f
  private const val EULER_Y_RIGHT_MAX = -15f
  private const val EULER_X_MAX = 20f
  private const val EULER_Z_MAX = 18f

  // Stable frame counter
  private const val REQUIRED_STABLE_FRAMES = 2
  ```

- [ ] **Step 3: Build để xác nhận không có lỗi compile**

  Trong Android Studio: **Build > Make Project** (hoặc `Ctrl+F9`).  
  Expected: BUILD SUCCESSFUL, không có lỗi hay warning mới.

- [ ] **Step 4: Commit**

  ```bash
  git add app/src/main/java/com/linh/banking_ekyc/ekyc/FaceValidation.kt
  git commit -m "feat: relax face validation thresholds for easier eKYC capture

  - MIN_FACE_RATIO: 0.35 -> 0.25 (accept smaller face in frame)
  - TOO_FAR_FACE_RATIO: 0.20 -> 0.15
  - EULER_Y_CENTER_MAX: 12 -> 18 (more head angle tolerance when looking straight)
  - EULER_Y_LEFT_MIN: 20 -> 15 (less rotation needed for turn phases)
  - EULER_Y_RIGHT_MAX: -20 -> -15
  - EULER_X_MAX: 15 -> 20 (more pitch tolerance)
  - EULER_Z_MAX: 15 -> 18 (more tilt tolerance)
  - BB_MAX_CHANGE_RATIO: 0.25 -> 0.35 (fewer false positive obstruction alerts)
  - BB_MAX_CENTER_SHIFT_RATIO: 0.20 -> 0.30
  - REQUIRED_STABLE_FRAMES: 3 -> 2 (shorter hold-still wait)"
  ```

---

### Task 2: Kiểm tra thủ công trên thiết bị

**Files:** Không có thay đổi code — đây là bước test thủ công.

- [ ] **Step 1: Chạy app trên thiết bị thật**

  Mở màn hình eKYC face scan. Kiểm tra 3 kịch bản:

  1. **FACE_CENTER** — nhìn thẳng vào camera, đầu hơi nghiêng ±15°. Kỳ vọng: app chấp nhận và chụp sau ~1-2 giây.
  2. **TURN_LEFT** — xoay đầu sang trái ~15-20°. Kỳ vọng: app chấp nhận, không yêu cầu xoay thêm.
  3. **TURN_RIGHT** — xoay đầu sang phải ~15-20°. Kỳ vọng: tương tự TURN_LEFT.

- [ ] **Step 2: Kiểm tra các guard vẫn hoạt động**

  1. Đeo khẩu trang → app vẫn hiện "Please remove your mask".
  2. Che tay lên mặt → app vẫn hiện "Face is partially covered".
  3. Nhắm mắt → app vẫn hiện "Open your eyes or remove sunglasses".

- [ ] **Step 3: Nếu cần fine-tune thêm**

  Nếu một phase vẫn còn khó, điều chỉnh thêm hằng số tương ứng:
  - Vẫn khó FACE_CENTER → tăng `EULER_Y_CENTER_MAX` lên 22f
  - Vẫn khó TURN_LEFT/RIGHT → giảm `EULER_Y_LEFT_MIN` xuống 12f / `EULER_Y_RIGHT_MAX` lên -12f
  - Vẫn báo BB unstable → tăng `BB_MAX_CHANGE_RATIO` lên 0.40f

  Sau mỗi chỉnh, lặp lại Step 1-2, rồi commit riêng.