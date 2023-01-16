import android.app.Activity
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.jizhiku.app.logic.utils.Logger
import kotlin.math.max
import kotlin.math.sqrt

/**
 * @author：FogSong
 * @time：2023-01-16  8:23
 */

@Composable
fun ThemeChangeAnimationContainer(
    activity: Activity,
    content: @Composable ThemeChangeAnimationContainerScope.() -> Unit
) {

    val scope = rememberCoroutineScope()
    val themeScope =
        remember { ThemeChangeAnimationContainerScope(activity, scope) }
    return Box(modifier = themeScope.modifier.value) {

        themeScope.content()
    }
}


fun captureView(view: View, window: Window, bitmapCallback: (Bitmap) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        // Above Android O, use PixelCopy
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        PixelCopy.request(
            window,
            Rect(location[0], location[1], location[0] + view.width, location[1] + view.height),
            bitmap,
            {
                if (it == PixelCopy.SUCCESS) {
                    bitmapCallback.invoke(bitmap)
                }
            },
            Handler(Looper.getMainLooper())
        )
    } else {
        val tBitmap = Bitmap.createBitmap(
            view.width, view.height, Bitmap.Config.RGB_565
        )
        val canvas = Canvas(tBitmap)
        view.draw(canvas)
        canvas.setBitmap(null)
        bitmapCallback.invoke(tBitmap)
    }
}

class ThemeChangeAnimationContainerScope(
    private val activity: Activity,
    private val scope: CoroutineScope
) {
    
    var isAnimating = false
        private set
    private var touchOffset = Offset.Zero
    private var preBitmap: Bitmap? = null
    var startRadius = 20f
    private var radius = Animatable(startRadius)

    var modifier = mutableStateOf(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        val pointEvent = awaitPointerEvent(PointerEventPass.Initial)
                        touchOffset = pointEvent.changes[0].position
                    }
                }
            })
        private set

    private fun getScreenShot(changeContent: () -> Unit) {
        captureView(activity.window.decorView, activity.window) {
            // 如果不是null，说明bitmap还在使用未被消除
            if (preBitmap == null) {
                preBitmap = it
                // 获取完后就更改
                changeContent()
            }
        }
    }

    // 结合点击位置获取最适合的radius
    private fun getRadius(): Float {
        val height = activity.window.decorView.height
        val width = activity.window.decorView.width
        val (x, y) = touchOffset
        val maxX = max(width - x, x)
        val maxY = max(height - y, y)
        return sqrt(maxX * maxX + maxY * maxY) + 5f
    }

    // 进行屏幕截取,获取后对box修饰
    fun startThemeChangeAnime(changeContent: () -> Unit) {
        val endRadius = getRadius()

        // 设置radius,并设置标志
        scope.launch {
            if (!isAnimating) {
                isAnimating = true
                radius.animateTo(
                    endRadius, tween(1000)
                )
            }
        }
        // 获取bitmap
        getScreenShot(changeContent)

        modifier.value = modifier.value.drawWithContent {
            drawContent()
            Logger.d(isAnimating.toString())
            // 判断是否在动画且得到了bitmap
            if (isAnimating && preBitmap != null) {
                // 绘制渐变
                val nativeCanvas = drawContext.canvas.nativeCanvas
                val painter = Paint()
                    .apply {
                        isAntiAlias = true
                        isDither = true
                        isFilterBitmap = true
                    }
                // 绘制扩大
                // 必须设置layer不然xfermode无效
                val savedLayer = nativeCanvas.saveLayer(0f, 0f, size.width, size.height, null)
                nativeCanvas.drawBitmap(preBitmap!!, 0f, 0f, null)
                painter.xfermode = PorterDuffXfermode(PorterDuff.Mode.XOR)
                // 绘制圆
                nativeCanvas.drawCircle(touchOffset.x, touchOffset.y, radius.value, painter)
                painter.xfermode = null
                // 恢复层
                nativeCanvas.restoreToCount(savedLayer)
                // 绘制完判断
                if (!radius.isRunning && isAnimating) {
                    scope.launch {
                        radius.snapTo(startRadius)
                        preBitmap = null
                        isAnimating = false
                    }
                }
            }
        }
    }
}
