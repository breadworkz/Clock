package app.materialclock.widget

import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.material3.MaterialShapes
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import app.materialclock.data.FaceShape
import app.materialclock.data.FitMode
import app.materialclock.data.PillOrientation
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Fitting a Material shape into a widget's rectangle so it touches all four edges and crosses none.
 *
 * Everything here is `android.graphics` and `androidx.graphics.shapes` only. The `toPath`/`toShape`
 * overloads in material3 take a `Composer` and cannot be called from a widget, but
 * `Shapes_androidKt.toPath(RoundedPolygon, android.graphics.Path)` is a plain function and is what
 * these use.
 */

/**
 * Maps the polygon's **exact ink bounds** onto the rectangle.
 *
 * `calculateBounds()` defaults to `approximate = true`, which returns the union of the Bézier
 * *control-point* boxes, a strict superset of the ink and not a tight one. Measured waste if you
 * trust it: Semicircle 18.75 %, Clamshell 16.28 %, Arrow 11.53 %, Cookie4Sided 6.22 % on all four
 * sides. Every one of those is a visible gap between the face and the widget's edge, which is
 * exactly what must not happen. `approximate = false` solves the cubic-derivative roots instead and
 * is exact.
 *
 * Affine maps commute with Bézier evaluation, so scaling control points and scaling the rendered
 * curve give the identical curve and the transform itself contributes no error.
 */
fun RoundedPolygon.fillRect(
    l: Float,
    t: Float,
    r: Float,
    b: Float,
    uniform: Boolean = false,
    out: Path = Path(),
    scratch: FloatArray = FloatArray(4),
): Path {
    val e = calculateBounds(scratch, /* approximate = */ false)
    val w = (e[2] - e[0]).takeIf { it > 1e-6f } ?: 1f
    val h = (e[3] - e[1]).takeIf { it > 1e-6f } ?: 1f
    var sx = (r - l) / w
    var sy = (b - t) / h
    var dx = l - e[0] * sx
    var dy = t - e[1] * sy
    if (uniform) {
        val s = min(sx, sy)
        sx = s
        sy = s
        dx = l + ((r - l) - w * s) / 2f - e[0] * s
        dy = t + ((b - t) - h * s) / 2f - e[1] * s
    }
    out.rewind()
    toPath(out)
    out.transform(Matrix().apply { setScale(sx, sy); postTranslate(dx, dy) })
    return out
}

/**
 * A diagonal stadium that inscribes the widget exactly, touching all four edges, with **no
 * distortion**. It is a true pill moved by a rotation and a translation, so its caps stay
 * perfectly circular.
 *
 * From a stadium's support function `h(n) = c·n + L|u·n| + r`, touching all four sides requires
 * `2(L|cos θ| + r) = W` and `2(L|sin θ| + r) = H`. Setting θ = 45° therefore *forces* W = H: a
 * 45° stadium's bounding box is always square. Dropping the angle constraint and keeping "touches
 * all four sides" leaves this one-parameter family in `r`, whose angle is
 * `atan2(H/2 − r, W/2 − r)`, or 12–18° on a wide widget. That is the honest diagonal; a
 * visually-45° pill that also fills a non-square box does not exist.
 *
 * `r → 0` degenerates to the rectangle's own diagonal; `r → min(W,H)/2` collapses to a horizontal
 * pill.
 */
fun diagonalPill(
    w: Float,
    h: Float,
    r: Float = 0.30f * min(w, h),
    descending: Boolean = true,
    out: Path = Path(),
): Path {
    val ax = w / 2f - r
    val ay = h / 2f - r
    if (ax <= 0f || ay <= 0f) {
        // Degenerate box, so fall back to the plain pill rather than throwing inside a receiver.
        return RoundedPolygon.pill(width = w, height = h, centerX = w / 2f, centerY = h / 2f)
            .toPath(out)
    }
    val degrees = Math.toDegrees(atan2(ay, ax).toDouble()).toFloat()
    val half = hypot(ax, ay)
    out.rewind()
    RoundedPolygon.pill(width = 2f * (half + r), height = 2f * r).toPath(out)
    out.transform(
        Matrix().apply {
            setRotate(if (descending) degrees else -degrees)
            postTranslate(w / 2f, h / 2f)
        }
    )
    return out
}

/**
 * The face outline for a configuration, in pixels, filling `w × h`.
 *
 * The four parametric shapes are *rebuilt* at the target aspect so their corners stay circular.
 * Everything else is a baked point list and can only be stretched, which turns circular roundings
 * into ellipses of semi-axes `(r·sx, r·sy)`. That is unavoidable, and it is the reason
 * [FitMode.UNIFORM] is offered.
 */
fun facePath(
    shape: FaceShape,
    pill: PillOrientation,
    fit: FitMode,
    w: Float,
    h: Float,
    out: Path = Path(),
): Path {
    val uniform = fit == FitMode.UNIFORM
    return when (shape) {
        FaceShape.CIRCLE ->
            RoundedPolygon.circle(numVertices = 24, radius = 0.5f, centerX = 0.5f, centerY = 0.5f)
                .fillRect(0f, 0f, w, h, uniform, out)

        FaceShape.SQUARE_SHARP ->
            RoundedPolygon.rectangle(w, h, CornerRounding.Unrounded, centerX = w / 2f, centerY = h / 2f)
                .toPath(out)

        FaceShape.ROUNDED_SQUARE ->
            RoundedPolygon.rectangle(
                width = w,
                height = h,
                rounding = CornerRounding(0.22f * min(w, h), smoothing = 0.6f),
                centerX = w / 2f,
                centerY = h / 2f,
            ).toPath(out)

        FaceShape.PILL -> when (pill) {
            PillOrientation.DIAGONAL -> diagonalPill(w, h, out = out)
            // `RoundedPolygon.pill` picks its own long axis from width vs height, so the two
            // upright orientations only differ when the widget is close to square.
            PillOrientation.VERTICAL ->
                RoundedPolygon.pill(min(w, h), h, centerX = w / 2f, centerY = h / 2f).toPath(out)
            PillOrientation.HORIZONTAL ->
                RoundedPolygon.pill(w, min(w, h), centerX = w / 2f, centerY = h / 2f).toPath(out)
        }

        else -> shape.materialShape().fillRect(0f, 0f, w, h, uniform, out)
    }
}

/**
 * The baked Material point list behind a non-parametric [FaceShape].
 *
 * Each getter returns a cached, already-normalised `RoundedPolygon`, so this is cheap and safe to
 * call per draw. Circle is the fallback rather than a throw: an enum value from a newer build must
 * degrade to a working clock.
 */
fun FaceShape.materialShape(): RoundedPolygon = when (this) {
    FaceShape.ARCH -> MaterialShapes.Arch
    FaceShape.ARROW -> MaterialShapes.Arrow
    FaceShape.BOOM -> MaterialShapes.Boom
    FaceShape.BUN -> MaterialShapes.Bun
    FaceShape.BURST -> MaterialShapes.Burst
    FaceShape.CLAM_SHELL -> MaterialShapes.ClamShell
    FaceShape.CLOVER_4 -> MaterialShapes.Clover4Leaf
    FaceShape.CLOVER_8 -> MaterialShapes.Clover8Leaf
    FaceShape.COOKIE_4 -> MaterialShapes.Cookie4Sided
    FaceShape.COOKIE_6 -> MaterialShapes.Cookie6Sided
    FaceShape.COOKIE_7 -> MaterialShapes.Cookie7Sided
    FaceShape.COOKIE_9 -> MaterialShapes.Cookie9Sided
    FaceShape.COOKIE_12 -> MaterialShapes.Cookie12Sided
    FaceShape.DIAMOND -> MaterialShapes.Diamond
    FaceShape.FAN -> MaterialShapes.Fan
    FaceShape.FLOWER -> MaterialShapes.Flower
    FaceShape.GEM -> MaterialShapes.Gem
    FaceShape.GHOSTISH -> MaterialShapes.Ghostish
    FaceShape.HEART -> MaterialShapes.Heart
    FaceShape.MATERIAL_PILL -> MaterialShapes.Pill
    FaceShape.OVAL -> MaterialShapes.Oval
    FaceShape.PENTAGON -> MaterialShapes.Pentagon
    FaceShape.PIXEL_CIRCLE -> MaterialShapes.PixelCircle
    FaceShape.PIXEL_TRIANGLE -> MaterialShapes.PixelTriangle
    FaceShape.PUFFY -> MaterialShapes.Puffy
    FaceShape.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond
    FaceShape.SEMI_CIRCLE -> MaterialShapes.SemiCircle
    FaceShape.SLANTED -> MaterialShapes.Slanted
    FaceShape.SOFT_BOOM -> MaterialShapes.SoftBoom
    FaceShape.SOFT_BURST -> MaterialShapes.SoftBurst
    FaceShape.SQUIRCLE -> MaterialShapes.Square
    FaceShape.SUNNY -> MaterialShapes.Sunny
    FaceShape.TRIANGLE -> MaterialShapes.Triangle
    FaceShape.VERY_SUNNY -> MaterialShapes.VerySunny
    else -> MaterialShapes.Circle
}
