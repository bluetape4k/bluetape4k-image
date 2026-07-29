package io.bluetape4k.images.captcha

import com.sksamuel.scrimage.ImmutableImage
import io.bluetape4k.images.withGraphics
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.security.SecureRandom
import java.time.Clock
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.asKotlinRandom
import kotlin.time.toJavaDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Java2D 기반 [CaptchaGenerator] 구현체입니다.
 *
 * rendering은 native library나 font asset을 bundle하지 않고 JVM logical font, bounded
 * noise, optional wave distortion을 사용합니다.
 */
internal class Java2dCaptchaGenerator(
    override val options: CaptchaOptions = CaptchaOptions(),
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val onBeforeRender: (() -> Unit)? = null,
): CaptchaGenerator {

    override fun generate(length: Int): CaptchaChallenge {
        CaptchaOptions.validateLength(length)
        val text = generateText(length)
        onBeforeRender?.invoke()
        return CaptchaChallenge(
            text = text,
            image = render(text),
            expiresAt = clock.instant().plus(options.expiresAfter.toJavaDuration()),
        )
    }

    override suspend fun generateSuspend(length: Int): CaptchaChallenge {
        currentCoroutineContext().ensureActive()
        return withContext(Dispatchers.Default) {
            currentCoroutineContext().ensureActive()
            generate(length)
        }
    }

    private fun generateText(length: Int): String =
        buildString(length) {
            repeat(length) {
                append(options.charSet[random.nextInt(options.charSet.length)])
            }
        }

    private fun render(text: String): ImmutableImage {
        val image = ImmutableImage
            .create(
                options.imageSize.width,
                options.imageSize.height,
                BufferedImage.TYPE_INT_ARGB,
            )
            .withGraphics { graphics ->
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                graphics.composite = AlphaComposite.SrcOver
                graphics.color = options.backgroundColor
                graphics.fillRect(0, 0, options.imageSize.width, options.imageSize.height)
                // 가독성과 replay resistance를 함께 얻기 위해 낮은 opacity의 noise를 text 뒤와 위에 모두 둡니다.
                drawNoise(graphics)
                drawText(graphics, text)
                drawNoise(graphics)
            }

        return when (val distortion = options.distortion) {
            CaptchaDistortion.None -> image
            is CaptchaDistortion.Wave -> image.applyWave(distortion)
        }
    }

    private fun drawNoise(graphics: Graphics2D) {
        repeat(options.noise.lines) {
            graphics.color = randomNoiseColor()
            val x1 = random.nextInt(options.imageSize.width)
            val y1 = random.nextInt(options.imageSize.height)
            val x2 = random.nextInt(options.imageSize.width)
            val y2 = random.nextInt(options.imageSize.height)
            graphics.drawLine(x1, y1, x2, y2)
        }
        repeat(options.noise.dots) {
            graphics.color = randomNoiseColor()
            val x = random.nextInt(options.imageSize.width)
            val y = random.nextInt(options.imageSize.height)
            graphics.fillOval(x, y, random.nextInt(1, 4), random.nextInt(1, 4))
        }
    }

    private fun drawText(graphics: Graphics2D, text: String) {
        val slotWidth = options.imageSize.width.toDouble() / text.length
        val baseline = (options.imageSize.height + options.fontSize) / 2.0 - (options.fontSize * 0.18)
        val kotlinRandom = random.asKotlinRandom()

        text.forEachIndexed { index, char ->
            val fontJitter = kotlinRandom.nextInt(-3, 4)
            graphics.font = options.fonts.random(kotlinRandom).toAwtFont((options.fontSize + fontJitter).coerceAtLeast(1))
            graphics.color = options.textColors.random(kotlinRandom)

            val centerX = slotWidth * index + slotWidth / 2.0
            val x = centerX - graphics.fontMetrics.charWidth(char) / 2.0
            val y = baseline + kotlinRandom.nextInt(-5, 6)
            val rotation = kotlinRandom.nextDouble(-0.35, 0.35)

            val original = graphics.transform
            graphics.rotate(rotation, centerX, y)
            graphics.drawString(char.toString(), x.toFloat(), y.toFloat())
            graphics.transform = original
        }
    }

    private fun randomNoiseColor(): Color {
        val color = options.textColors[random.nextInt(options.textColors.size)]
        return Color(color.red, color.green, color.blue, 96)
    }

    private fun ImmutableImage.applyWave(distortion: CaptchaDistortion.Wave): ImmutableImage {
        if (distortion.strength == 0.0f) return this

        val source = awt()
        val target = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val amplitude = (distortion.strength * MAX_WAVE_AMPLITUDE).coerceAtLeast(0.0f)

        for (y in 0 until source.height) {
            val offset = (sin(y.toDouble() / source.height * PI * 2.0) * amplitude).roundToInt()
            for (x in 0 until source.width) {
                val sourceX = (x + offset).coerceIn(0, source.width - 1)
                target.setRGB(x, y, source.getRGB(sourceX, y))
            }
        }
        return ImmutableImage.fromAwt(target)
    }

    private companion object {
        private const val MAX_WAVE_AMPLITUDE: Float = 8.0f
    }
}
