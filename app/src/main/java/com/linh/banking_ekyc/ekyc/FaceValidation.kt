package com.linh.banking_ekyc.ekyc

import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark

/**
 * Validation result for face quality checks during eKYC scan.
 *
 * Uses ML Kit's classification probabilities and landmark detection to determine
 * whether the face is obscured by masks, sunglasses, or other objects.
 */
data class FaceValidationResult(
    val isValid: Boolean,
    val warningMessage: String,
    val hasMask: Boolean = false,
    val hasSunglasses: Boolean = false,
    val isFaceObstructed: Boolean = false,
    val eyesOpen: Boolean = true,
    val isFaceCloseEnough: Boolean = true
) {
    companion object {
        val VALID = FaceValidationResult(isValid = true, warningMessage = "")
    }
}

/**
 * Validates a detected face for eKYC quality requirements.
 *
 * ML Kit Face Detection provides:
 * - `leftEyeOpenProbability` / `rightEyeOpenProbability`: 0.0 to 1.0
 *   → Sunglasses typically block eye detection, resulting in null or very low probability.
 * - `smilingProbability`: not directly used, but confirms face is real.
 * - Landmarks (nose, mouth, eyes, ears, cheeks): missing landmarks indicate occlusion.
 * - `boundingBox`: for face-size-to-frame ratio.
 *
 * Obstruction detection strategy:
 * 1. **Sunglasses**: Eye open probabilities are null or < 0.15 when eyes are actually open
 *    (head facing camera). Combined with missing eye landmarks.
 * 2. **Mask**: Missing nose base, mouth landmarks, and lower face landmarks.
 * 3. **General obstruction**: Multiple key landmarks missing simultaneously.
 */
object FaceValidator {

    // Thresholds
    private const val EYE_OPEN_MIN_PROB = 0.3f       // Below this = eyes likely obscured
    private const val MIN_FACE_RATIO = 0.55f           // Face must be at least 55% of frame width (fills the overlay)
    private const val TOO_FAR_FACE_RATIO = 0.35f       // Below this = face is way too far

    // Euler angle thresholds for head pose
    private const val EULER_Y_CENTER_MAX = 12f         // Max Y rotation for "looking straight"
    private const val EULER_Y_LEFT_MIN = 20f           // Min Y rotation for "looking left"
    private const val EULER_Y_RIGHT_MAX = -20f         // Max Y rotation (negative) for "looking right"
    private const val EULER_X_MAX = 15f                // Max X rotation (tilt up/down)
    private const val EULER_Z_MAX = 15f                // Max Z rotation (head tilt)

    /**
     * Performs full face validation for eKYC capture readiness.
     *
     * @param face The ML Kit Face object
     * @param imageWidth Width of the camera frame
     * @param scanPhase Current scan phase (determines head pose requirements)
     * @return FaceValidationResult with validation status and warning messages
     */
    fun validate(
        face: Face,
        imageWidth: Int,
        scanPhase: ScanPhase? = null
    ): FaceValidationResult {
        // 1. Check for sunglasses (eyes obscured)
        val sunglassesCheck = checkSunglasses(face)
        if (sunglassesCheck != null) return sunglassesCheck

        // 2. Check for mask (mouth/nose obscured)
        val maskCheck = checkMask(face)
        if (maskCheck != null) return maskCheck

        // 3. Check for general face obstruction
        val obstructionCheck = checkObstruction(face)
        if (obstructionCheck != null) return obstructionCheck

        // 4. Check face size (close enough to camera - must fill the overlay)
        val faceRatio = face.boundingBox.width().toFloat() / imageWidth.coerceAtLeast(1)
        if (faceRatio < TOO_FAR_FACE_RATIO) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Move much closer to the camera",
                isFaceCloseEnough = false
            )
        }
        if (faceRatio < MIN_FACE_RATIO) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Move closer — fill the circle with your face",
                isFaceCloseEnough = false
            )
        }

        // 5. Check head tilt (Z axis) - too much tilt = bad photo quality
        val eulerZ = face.headEulerAngleZ
        if (Math.abs(eulerZ) > EULER_Z_MAX) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Keep your head straight"
            )
        }

        // 6. Check head tilt up/down (X axis)
        val eulerX = face.headEulerAngleX
        if (Math.abs(eulerX) > EULER_X_MAX) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = if (eulerX > 0) "Tilt your chin down slightly" else "Lift your chin up slightly"
            )
        }

        // 7. Phase-specific head pose validation
        if (scanPhase != null) {
            val poseCheck = checkHeadPose(face, scanPhase)
            if (poseCheck != null) return poseCheck
        }

        return FaceValidationResult.VALID
    }

    /**
     * Check for sunglasses using eye open probabilities and eye landmarks.
     *
     * ML Kit returns null for eye probabilities when the eyes cannot be classified
     * (i.e., obscured by sunglasses). When eyes are visible but closed, the probability
     * is typically > 0.05. Sunglasses give null or extremely low values.
     */
    private fun checkSunglasses(face: Face): FaceValidationResult? {
        val leftEyeProb = face.leftEyeOpenProbability
        val rightEyeProb = face.rightEyeOpenProbability

        // If both eye probabilities are null, ML Kit couldn't detect eyes at all
        // This strongly suggests sunglasses or heavy occlusion
        if (leftEyeProb == null && rightEyeProb == null) {
            // Double-check: if eye landmarks are also missing, very likely sunglasses
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            if (leftEye == null && rightEye == null) {
                return FaceValidationResult(
                    isValid = false,
                    warningMessage = "Please remove your sunglasses",
                    hasSunglasses = true,
                    isFaceObstructed = true
                )
            }
        }

        // Both eyes have very low probability while head is facing forward
        // This indicates eyes are blocked (sunglasses) rather than closed
        if (leftEyeProb != null && rightEyeProb != null) {
            if (leftEyeProb < EYE_OPEN_MIN_PROB && rightEyeProb < EYE_OPEN_MIN_PROB) {
                // Check if head is roughly facing forward (not extreme angle)
                val isHeadForward = Math.abs(face.headEulerAngleY) < 30f
                if (isHeadForward) {
                    return FaceValidationResult(
                        isValid = false,
                        warningMessage = "Open your eyes or remove sunglasses",
                        hasSunglasses = true,
                        eyesOpen = false
                    )
                }
            }
        }

        return null // No sunglasses detected
    }

    /**
     * Check for face mask by analyzing mouth/nose/chin landmark availability
     * and the lower face region.
     *
     * When wearing a mask:
     * - Nose base landmark is typically missing or mis-detected
     * - Mouth landmarks (left, right, bottom) are missing
     * - Cheek landmarks may be partially missing
     */
    private fun checkMask(face: Face): FaceValidationResult? {
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)

        // Count missing lower-face landmarks
        var missingLowerFace = 0
        if (mouthLeft == null) missingLowerFace++
        if (mouthRight == null) missingLowerFace++
        if (mouthBottom == null) missingLowerFace++
        if (noseBase == null) missingLowerFace++
        if (leftCheek == null) missingLowerFace++
        if (rightCheek == null) missingLowerFace++

        // If 4+ lower face landmarks are missing, likely wearing a mask
        if (missingLowerFace >= 4) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Please remove your mask",
                hasMask = true,
                isFaceObstructed = true
            )
        }

        // If mouth landmarks are all missing but nose is present = partial mask
        if (mouthLeft == null && mouthRight == null && mouthBottom == null) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Please remove your mask",
                hasMask = true,
                isFaceObstructed = true
            )
        }

        return null // No mask detected
    }

    /**
     * Check for general face obstruction.
     * If many key landmarks are missing, something is blocking the face.
     */
    private fun checkObstruction(face: Face): FaceValidationResult? {
        val keyLandmarks = listOf(
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.RIGHT_CHEEK,
            FaceLandmark.LEFT_EAR,
            FaceLandmark.RIGHT_EAR
        )

        val missingCount = keyLandmarks.count { face.getLandmark(it) == null }

        // If more than half of key landmarks are missing, face is obstructed
        if (missingCount >= 6) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Face is obstructed.\nPlease remove any objects",
                isFaceObstructed = true
            )
        }

        return null
    }

    /**
     * Validate head pose for the current scan phase.
     */
    private fun checkHeadPose(face: Face, scanPhase: ScanPhase): FaceValidationResult? {
        val eulerY = face.headEulerAngleY

        return when (scanPhase) {
            ScanPhase.FACE_CENTER -> {
                if (Math.abs(eulerY) > EULER_Y_CENTER_MAX) {
                    FaceValidationResult(
                        isValid = false,
                        warningMessage = "Look straight at the camera"
                    )
                } else null
            }
            ScanPhase.TURN_LEFT -> {
                if (eulerY < EULER_Y_LEFT_MIN) {
                    FaceValidationResult(
                        isValid = false,
                        warningMessage = "Turn your head further left"
                    )
                } else null
            }
            ScanPhase.TURN_RIGHT -> {
                if (eulerY > EULER_Y_RIGHT_MAX) {
                    FaceValidationResult(
                        isValid = false,
                        warningMessage = "Turn your head further right"
                    )
                } else null
            }
            ScanPhase.COMPLETED -> null
        }
    }
}
