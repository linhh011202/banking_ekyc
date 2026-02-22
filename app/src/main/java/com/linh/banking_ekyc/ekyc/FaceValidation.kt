package com.linh.banking_ekyc.ekyc

import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.math.abs

/**
 * Validation result for face quality checks during eKYC scan.
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

object FaceValidator {

    private const val TAG = "FaceValidator"

    // ── Thresholds ──────────────────────────────────────────────────────
    private const val EYE_OPEN_MIN_PROB = 0.25f
    private const val MIN_FACE_RATIO = 0.35f
    private const val TOO_FAR_FACE_RATIO = 0.20f

    // Bounding box aspect ratio (width / height). Normal frontal face ≈ 0.65 – 1.15
    private const val BB_ASPECT_RATIO_MIN = 0.50f
    private const val BB_ASPECT_RATIO_MAX = 1.40f

    // Contour: ML Kit returns ~36 points for FACE contour when fully visible
    private const val MIN_FACE_CONTOUR_POINTS = 15

    // Bounding box stability: max allowed change ratio between frames
    // If BB width or height changes by more than 25% between frames → obstruction
    private const val BB_MAX_CHANGE_RATIO = 0.25f
    // If BB center shifts by more than 20% of BB width between frames → obstruction
    private const val BB_MAX_CENTER_SHIFT_RATIO = 0.20f

    // Euler angle thresholds
    private const val EULER_Y_CENTER_MAX = 12f
    private const val EULER_Y_LEFT_MIN = 20f
    private const val EULER_Y_RIGHT_MAX = -20f
    private const val EULER_X_MAX = 15f
    private const val EULER_Z_MAX = 15f

    // Stable frame counter
    private const val REQUIRED_STABLE_FRAMES = 3
    private var consecutiveValidFrames = 0

    // Previous frame's bounding box for stability tracking
    private var prevBBWidth = 0f
    private var prevBBHeight = 0f
    private var prevBBCenterX = 0f
    private var prevBBCenterY = 0f
    private var hasPrevBB = false

    fun resetStableCounter() {
        consecutiveValidFrames = 0
        hasPrevBB = false
    }

    /** Helper: is head turned significantly? */
    private fun isHeadTurned(face: Face): Boolean = abs(face.headEulerAngleY) > 18f

    /** Helper: is this a turn phase? */
    private fun isTurnPhase(scanPhase: ScanPhase?): Boolean =
        scanPhase == ScanPhase.TURN_LEFT || scanPhase == ScanPhase.TURN_RIGHT

    // ── Main validation ─────────────────────────────────────────────────

    fun validate(
        face: Face,
        imageWidth: Int,
        scanPhase: ScanPhase? = null
    ): FaceValidationResult {
        val headTurned = isHeadTurned(face)
        val turnPhase = isTurnPhase(scanPhase)

        // 1. Sunglasses — only when head is roughly forward
        if (!headTurned) {
            val sunglassesCheck = checkSunglasses(face)
            if (sunglassesCheck != null) {
                consecutiveValidFrames = 0
                return sunglassesCheck
            }
        }

        // 2. Mask — only when head is roughly forward
        //    When head is turned, mouth/cheek landmarks on the far side naturally disappear
        if (!headTurned) {
            val maskCheck = checkMask(face)
            if (maskCheck != null) {
                consecutiveValidFrames = 0
                return maskCheck
            }
        }

        // 3. Contour-based obstruction (relaxed thresholds for turn phases)
        val contourCheck = checkContourObstruction(face, turnPhase)
        if (contourCheck != null) {
            consecutiveValidFrames = 0
            return contourCheck
        }

        // 4. Bounding box abnormality — SKIP during turn phases
        //    When head is turned, BB naturally becomes narrower → different aspect ratio
        if (!turnPhase) {
            val bbCheck = checkBoundingBoxAbnormality(face)
            if (bbCheck != null) {
                consecutiveValidFrames = 0
                return bbCheck
            }
        }

        // 5. General obstruction (relaxed for turn phases)
        val obstructionCheck = checkObstruction(face, scanPhase)
        if (obstructionCheck != null) {
            consecutiveValidFrames = 0
            return obstructionCheck
        }

        // 6. Face size — more lenient for turn phases (face appears smaller when turned)
        val faceRatio = face.boundingBox.width().toFloat() / imageWidth.coerceAtLeast(1)
        val minRatio = if (turnPhase) TOO_FAR_FACE_RATIO else MIN_FACE_RATIO
        val tooFarRatio = if (turnPhase) 0.15f else TOO_FAR_FACE_RATIO

        if (faceRatio < tooFarRatio) {
            consecutiveValidFrames = 0
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Move much closer to the camera",
                isFaceCloseEnough = false
            )
        }
        if (faceRatio < minRatio) {
            consecutiveValidFrames = 0
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Move closer — fill the circle with your face",
                isFaceCloseEnough = false
            )
        }

        // 7. Head tilt Z — slightly more lenient for turn phases
        val maxZ = if (turnPhase) EULER_Z_MAX + 5f else EULER_Z_MAX
        if (abs(face.headEulerAngleZ) > maxZ) {
            consecutiveValidFrames = 0
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Keep your head straight"
            )
        }

        // 8. Head tilt X
        val eulerX = face.headEulerAngleX
        if (abs(eulerX) > EULER_X_MAX) {
            consecutiveValidFrames = 0
            return FaceValidationResult(
                isValid = false,
                warningMessage = if (eulerX > 0) "Tilt your chin down slightly" else "Lift your chin up slightly"
            )
        }

        // 9. Phase-specific head pose
        if (scanPhase != null) {
            val poseCheck = checkHeadPose(face, scanPhase)
            if (poseCheck != null) {
                consecutiveValidFrames = 0
                return poseCheck
            }
        }

        // 10. Bounding box stability — detect sudden changes from obstruction
        val stabilityCheck = checkBBStability(face)
        if (stabilityCheck != null) {
            consecutiveValidFrames = 0
            return stabilityCheck
        }

        // Update previous BB for next frame comparison
        updatePrevBB(face)

        // All passed → stable counter
        consecutiveValidFrames++
        Log.d(TAG, "Stable valid frames: $consecutiveValidFrames/$REQUIRED_STABLE_FRAMES")

        if (consecutiveValidFrames < REQUIRED_STABLE_FRAMES) {
            return FaceValidationResult(isValid = false, warningMessage = "Hold still...")
        }

        return FaceValidationResult.VALID
    }

    /**
     * Lightweight re-validation used DURING capture.
     * Only checks for clear obstructions + BB stability. Does NOT require stable frames.
     */
    fun quickCheck(face: Face, scanPhase: ScanPhase? = null): FaceValidationResult {
        val headTurned = isHeadTurned(face)
        val turnPhase = isTurnPhase(scanPhase)

        if (!headTurned) {
            checkSunglasses(face)?.let { return it }
            checkMask(face)?.let { return it }
        }
        checkContourObstruction(face, turnPhase)?.let { return it }
        if (!turnPhase) {
            checkBoundingBoxAbnormality(face)?.let { return it }
        }
        checkObstruction(face, scanPhase)?.let { return it }

        // Also check BB stability during capture
        val stabilityCheck = checkBBStability(face)
        if (stabilityCheck != null) return stabilityCheck

        updatePrevBB(face)
        return FaceValidationResult.VALID
    }

    // ─── Sunglasses ──────────────────────────────────────────────────────

    private fun checkSunglasses(face: Face): FaceValidationResult? {
        val leftEyeProb = face.leftEyeOpenProbability
        val rightEyeProb = face.rightEyeOpenProbability

        // Both null + at least one eye landmark missing
        if (leftEyeProb == null && rightEyeProb == null) {
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)
            if (leftEye == null || rightEye == null) {
                return FaceValidationResult(
                    isValid = false,
                    warningMessage = "Please remove your sunglasses",
                    hasSunglasses = true,
                    isFaceObstructed = true
                )
            }
        }

        // Both eyes very low probability
        if (leftEyeProb != null && rightEyeProb != null &&
            leftEyeProb < EYE_OPEN_MIN_PROB && rightEyeProb < EYE_OPEN_MIN_PROB
        ) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Open your eyes or remove sunglasses",
                hasSunglasses = true,
                eyesOpen = false
            )
        }

        return null
    }

    // ─── Mask ────────────────────────────────────────────────────────────

    private fun checkMask(face: Face): FaceValidationResult? {
        val noseBase = face.getLandmark(FaceLandmark.NOSE_BASE)
        val mouthLeft = face.getLandmark(FaceLandmark.MOUTH_LEFT)
        val mouthRight = face.getLandmark(FaceLandmark.MOUTH_RIGHT)
        val mouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)
        val leftCheek = face.getLandmark(FaceLandmark.LEFT_CHEEK)
        val rightCheek = face.getLandmark(FaceLandmark.RIGHT_CHEEK)

        var missingLowerFace = 0
        if (mouthLeft == null) missingLowerFace++
        if (mouthRight == null) missingLowerFace++
        if (mouthBottom == null) missingLowerFace++
        if (noseBase == null) missingLowerFace++
        if (leftCheek == null) missingLowerFace++
        if (rightCheek == null) missingLowerFace++

        // 3+ lower-face landmarks missing → likely mask
        if (missingLowerFace >= 3) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Please remove your mask",
                hasMask = true,
                isFaceObstructed = true
            )
        }

        // Lower lip + upper lip contours BOTH absent while nose bridge exists
        val lowerLipBottom = face.getContour(FaceContour.LOWER_LIP_BOTTOM)
        val upperLipTop = face.getContour(FaceContour.UPPER_LIP_TOP)
        val noseBridge = face.getContour(FaceContour.NOSE_BRIDGE)
        if (lowerLipBottom == null && upperLipTop == null && noseBridge != null) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Please remove your mask",
                hasMask = true,
                isFaceObstructed = true
            )
        }

        return null
    }

    // ─── Contour-based obstruction ───────────────────────────────────────

    /**
     * Checks face contour completeness.
     *
     * @param isTurnPhase If true, uses a more lenient threshold (6+) because
     *   contours on the far side of the face naturally disappear when head is turned.
     */
    private fun checkContourObstruction(face: Face, isTurnPhase: Boolean): FaceValidationResult? {
        val contourTypes = listOf(
            FaceContour.FACE,
            FaceContour.LEFT_EYE,
            FaceContour.RIGHT_EYE,
            FaceContour.UPPER_LIP_TOP,
            FaceContour.LOWER_LIP_BOTTOM,
            FaceContour.NOSE_BRIDGE,
            FaceContour.NOSE_BOTTOM,
            FaceContour.LEFT_EYEBROW_TOP,
            FaceContour.RIGHT_EYEBROW_TOP
        )

        var missingContours = 0
        for (type in contourTypes) {
            val contour = face.getContour(type)
            if (contour == null || contour.points.isEmpty()) {
                missingContours++
            }
        }

        val faceContour = face.getContour(FaceContour.FACE)
        Log.d(TAG, "Contour check: missing=$missingContours/9, facePoints=${faceContour?.points?.size ?: 0}, turnPhase=$isTurnPhase")

        // Threshold depends on whether user is in a turn phase
        // Turn phases: far-side eye, eyebrow, and lip contours naturally disappear → need 6+
        // Frontal phase: 4+ is suspicious
        val threshold = if (isTurnPhase) 6 else 4

        if (missingContours >= threshold) {
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Face is partially covered.\nPlease remove any objects",
                isFaceObstructed = true
            )
        }

        // Face outline too disrupted + some other contours missing (only for frontal)
        if (!isTurnPhase) {
            val faceContourTooFew = faceContour != null && faceContour.points.size < MIN_FACE_CONTOUR_POINTS
            if (faceContourTooFew && missingContours >= 2) {
                return FaceValidationResult(
                    isValid = false,
                    warningMessage = "Face is partially covered.\nPlease remove any objects",
                    isFaceObstructed = true
                )
            }
        }

        return null
    }

    // ─── Bounding box abnormality ────────────────────────────────────────

    private fun checkBoundingBoxAbnormality(face: Face): FaceValidationResult? {
        val bb = face.boundingBox
        val width = bb.width().toFloat()
        val height = bb.height().toFloat()
        if (height <= 0 || width <= 0) return null

        val aspectRatio = width / height

        if (aspectRatio < BB_ASPECT_RATIO_MIN || aspectRatio > BB_ASPECT_RATIO_MAX) {
            Log.d(TAG, "Abnormal BB ratio: $aspectRatio (range $BB_ASPECT_RATIO_MIN–$BB_ASPECT_RATIO_MAX)")
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Face is partially covered.\nPlease remove any objects",
                isFaceObstructed = true
            )
        }

        return null
    }

    // ─── General obstruction ─────────────────────────────────────────────

    /**
     * Checks if too many key landmarks are missing simultaneously.
     *
     * When head is turned (left/right phase), many landmarks on the far side
     * naturally disappear — so we use a much higher threshold (5+) compared
     * to frontal (3+).
     */
    private fun checkObstruction(face: Face, scanPhase: ScanPhase?): FaceValidationResult? {
        val isCenterPhase = scanPhase == null || scanPhase == ScanPhase.FACE_CENTER
        val isHeadForward = abs(face.headEulerAngleY) < 20f

        // Core facial landmarks (no ears — ears are unreliable)
        val coreLandmarks = listOf(
            FaceLandmark.LEFT_EYE,
            FaceLandmark.RIGHT_EYE,
            FaceLandmark.NOSE_BASE,
            FaceLandmark.MOUTH_LEFT,
            FaceLandmark.MOUTH_RIGHT,
            FaceLandmark.MOUTH_BOTTOM,
            FaceLandmark.LEFT_CHEEK,
            FaceLandmark.RIGHT_CHEEK
        )

        val missingCore = coreLandmarks.count { face.getLandmark(it) == null }

        if (isCenterPhase && isHeadForward) {
            // Frontal: 3+ missing is suspicious
            if (missingCore >= 3) {
                Log.d(TAG, "Obstruction (frontal): $missingCore/8 core landmarks missing")
                return FaceValidationResult(
                    isValid = false,
                    warningMessage = "Face is obstructed.\nPlease remove any objects",
                    isFaceObstructed = true
                )
            }
        } else {
            // Turn phases: only flag when 5+ core landmarks missing
            // When turning, far-side eye, cheek, mouth corner, ear naturally disappear
            if (missingCore >= 5) {
                Log.d(TAG, "Obstruction (turned): $missingCore/8 core landmarks missing")
                return FaceValidationResult(
                    isValid = false,
                    warningMessage = "Face is obstructed.\nPlease remove any objects",
                    isFaceObstructed = true
                )
            }
        }

        return null
    }

    // ─── Bounding box stability ─────────────────────────────────────────

    /**
     * Detects sudden bounding box changes between frames.
     * When a hand/object covers part of the face, ML Kit still detects the face
     * but the bounding box shrinks, shifts, or changes aspect ratio abruptly.
     * This is the most reliable way to detect partial obstruction since ML Kit
     * infers landmarks/contours even when partially covered.
     */
    private fun checkBBStability(face: Face): FaceValidationResult? {
        if (!hasPrevBB) return null // First frame, nothing to compare

        val bb = face.boundingBox
        val curW = bb.width().toFloat()
        val curH = bb.height().toFloat()
        val curCX = bb.centerX().toFloat()
        val curCY = bb.centerY().toFloat()

        if (curW <= 0 || curH <= 0 || prevBBWidth <= 0 || prevBBHeight <= 0) return null

        // Check width change ratio
        val widthChange = abs(curW - prevBBWidth) / prevBBWidth
        val heightChange = abs(curH - prevBBHeight) / prevBBHeight

        // Check center shift relative to face size
        val centerShiftX = abs(curCX - prevBBCenterX) / prevBBWidth
        val centerShiftY = abs(curCY - prevBBCenterY) / prevBBHeight

        if (widthChange > BB_MAX_CHANGE_RATIO || heightChange > BB_MAX_CHANGE_RATIO) {
            Log.d(TAG, "BB unstable: widthΔ=${(widthChange * 100).toInt()}%, heightΔ=${(heightChange * 100).toInt()}%")
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Face is partially covered.\nPlease remove any objects",
                isFaceObstructed = true
            )
        }

        if (centerShiftX > BB_MAX_CENTER_SHIFT_RATIO || centerShiftY > BB_MAX_CENTER_SHIFT_RATIO) {
            Log.d(TAG, "BB center shifted: ΔX=${(centerShiftX * 100).toInt()}%, ΔY=${(centerShiftY * 100).toInt()}%")
            return FaceValidationResult(
                isValid = false,
                warningMessage = "Face is partially covered.\nPlease remove any objects",
                isFaceObstructed = true
            )
        }

        return null
    }

    /** Update stored bounding box for next frame comparison. */
    private fun updatePrevBB(face: Face) {
        val bb = face.boundingBox
        prevBBWidth = bb.width().toFloat()
        prevBBHeight = bb.height().toFloat()
        prevBBCenterX = bb.centerX().toFloat()
        prevBBCenterY = bb.centerY().toFloat()
        hasPrevBB = true
    }

    // ─── Head pose per phase ─────────────────────────────────────────────

    private fun checkHeadPose(face: Face, scanPhase: ScanPhase): FaceValidationResult? {
        val eulerY = face.headEulerAngleY

        return when (scanPhase) {
            ScanPhase.FACE_CENTER -> {
                if (abs(eulerY) > EULER_Y_CENTER_MAX) {
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
