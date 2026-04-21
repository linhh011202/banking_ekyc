# Design: Nới lỏng ngưỡng nhận diện khuôn mặt eKYC

**Date:** 2026-04-21  
**File affected:** `app/src/main/java/com/linh/banking_ekyc/ekyc/FaceValidation.kt`

## Vấn đề

App hiện tại quá strict khi nhận diện khuôn mặt trong quá trình eKYC:
- Giai đoạn FACE_CENTER: chỉ cho phép lệch ±12° nên user phải giữ đầu rất thẳng
- Giai đoạn TURN_LEFT/RIGHT: yêu cầu xoay ≥20° nên user phải xoay nhiều
- BB stability check quá nhạy gây false positive thường xuyên
- Cần 3 frame liên tiếp pass → thời gian chờ lâu

## Giải pháp

Chỉ chỉnh các hằng số threshold trong `FaceValidator`. Không thay đổi logic, không thêm class mới.

## Thay đổi threshold

| Hằng số | Cũ | Mới | Lý do |
|---|---|---|---|
| `MIN_FACE_RATIO` | 0.35 | 0.25 | Mặt nhỏ hơn vẫn chấp nhận được |
| `TOO_FAR_FACE_RATIO` | 0.20 | 0.15 | Hạ ngưỡng "quá xa" |
| `EULER_Y_CENTER_MAX` | 12° | 18° | Cho phép lệch ngang nhiều hơn khi nhìn thẳng |
| `EULER_Y_LEFT_MIN` | 20° | 15° | Giảm góc xoay trái cần thiết |
| `EULER_Y_RIGHT_MAX` | -20° | -15° | Giảm góc xoay phải cần thiết |
| `EULER_X_MAX` | 15° | 20° | Ngẩng/cúi đầu nhẹ vẫn pass |
| `EULER_Z_MAX` | 15° | 18° | Nghiêng đầu nhẹ vẫn pass |
| `BB_MAX_CHANGE_RATIO` | 0.25 | 0.35 | Giảm false positive từ chuyển động tự nhiên |
| `BB_MAX_CENTER_SHIFT_RATIO` | 0.20 | 0.30 | Giảm false positive từ dịch chuyển tự nhiên |
| `REQUIRED_STABLE_FRAMES` | 3 | 2 | Giảm thời gian "hold still" |

## Giữ nguyên

- Kiểm tra mask (missingLowerFace >= 3)
- Kiểm tra sunglasses (EYE_OPEN_MIN_PROB = 0.25)
- Contour obstruction (threshold frontal = 4, turn = 6)
- MIN_FACE_CONTOUR_POINTS = 15
- BB_ASPECT_RATIO_MIN/MAX (0.50 – 1.40)
- Post-capture photo validation (validateCapturedPhoto)

## Phạm vi

Chỉ ảnh hưởng `FaceValidation.kt`. Không thay đổi UI, logic capture, hay network layer.