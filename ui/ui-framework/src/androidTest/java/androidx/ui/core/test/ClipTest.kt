/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.ui.core.test

import android.graphics.Bitmap
import android.os.Build
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.test.rule.ActivityTestRule
import androidx.ui.core.DrawLayerModifier
import androidx.ui.core.Modifier
import androidx.ui.core.DrawScope
import androidx.ui.core.clip
import androidx.ui.core.clipToBounds
import androidx.ui.core.drawBehind
import androidx.ui.core.setContent
import androidx.ui.framework.test.TestActivity
import androidx.ui.geometry.RRect
import androidx.ui.geometry.Radius
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Color
import androidx.ui.graphics.Outline
import androidx.ui.graphics.Paint
import androidx.ui.graphics.Path
import androidx.ui.graphics.PathOperation
import androidx.ui.graphics.Shape
import androidx.ui.unit.Density
import androidx.ui.unit.PxSize
import androidx.ui.unit.ipx
import androidx.ui.unit.toRect
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(JUnit4::class)
class ClipTest {

    @get:Rule
    val rule = ActivityTestRule<TestActivity>(TestActivity::class.java)
    private lateinit var activity: TestActivity
    private lateinit var drawLatch: CountDownLatch

    private val rectShape = object : Shape {
        override fun createOutline(size: PxSize, density: Density): Outline =
            Outline.Rectangle(size.toRect())
    }
    private val triangleShape = object : Shape {
        override fun createOutline(size: PxSize, density: Density): Outline =
            Outline.Generic(
                Path().apply {
                    moveTo(size.width.value / 2f, 0f)
                    lineTo(size.width.value, size.height.value)
                    lineTo(0f, size.height.value)
                    close()
                }
            )
    }
    private val invertedTriangleShape = object : Shape {
        override fun createOutline(size: PxSize, density: Density): Outline =
            Outline.Generic(
                Path().apply {
                    lineTo(size.width.value, 0f)
                    lineTo(size.width.value / 2f, size.height.value)
                    lineTo(0f, 0f)
                    close()
                }
            )
    }

    @Before
    fun setup() {
        activity = rule.activity
        activity.hasFocusLatch.await(5, TimeUnit.SECONDS)
        drawLatch = CountDownLatch(1)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun simpleRectClip() {
        rule.runOnUiThreadIR {
            activity.setContent {
                Padding(size = 10.ipx, modifier = FillColor(Color.Green)) {
                    AtLeastSize(
                        size = 10.ipx,
                        modifier = Modifier.clip(rectShape) + FillColor(Color.Cyan)
                    ) {
                    }
                }
            }
        }

        takeScreenShot(30).apply {
            assertRect(Color.Cyan, size = 10)
            assertRect(Color.Green, holeSize = 10)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun simpleClipToBounds() {
        rule.runOnUiThreadIR {
            activity.setContent {
                Padding(size = 10.ipx, modifier = FillColor(Color.Green)) {
                    AtLeastSize(
                        size = 10.ipx,
                        modifier = Modifier.clipToBounds() + FillColor(Color.Cyan)
                    ) {
                    }
                }
            }
        }

        takeScreenShot(30).apply {
            assertRect(Color.Cyan, size = 10)
            assertRect(Color.Green, holeSize = 10)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun simpleRectClipWithModifiers() {
        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 10.ipx,
                    modifier = FillColor(Color.Green) + PaddingModifier(10.ipx) +
                            Modifier.clip(rectShape) + FillColor(Color.Cyan)
                ) {
                }
            }
        }

        takeScreenShot(30).apply {
            assertRect(Color.Cyan, size = 10)
            assertRect(Color.Green, holeSize = 10)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun roundedUniformRectClip() {
        val shape = object : Shape {
            override fun createOutline(size: PxSize, density: Density): Outline =
                    Outline.Rounded(RRect(size.toRect(), Radius.circular(12f)))
        }
        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 30.ipx,
                    modifier = FillColor(Color.Green) + Modifier.clip(shape) + FillColor(Color.Cyan)
                ) {
                }
            }
        }

        takeScreenShot(30).apply {
            // check corners
            assertColor(Color.Green, 2, 2)
            assertColor(Color.Green, 2, 27)
            assertColor(Color.Green, 2, 27)
            assertColor(Color.Green, 27, 2)
            // check inner rect
            assertRect(Color.Cyan, size = 18)
            // check centers of all sides
            assertColor(Color.Cyan, 0, 14)
            assertColor(Color.Cyan, 29, 14)
            assertColor(Color.Cyan, 14, 0)
            assertColor(Color.Cyan, 14, 29)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun roundedRectWithDiffCornersClip() {
        val shape = object : Shape {
            override fun createOutline(size: PxSize, density: Density): Outline =
                Outline.Rounded(
                    RRect(size.toRect(),
                        Radius.zero,
                        Radius.circular(12f),
                        Radius.circular(12f),
                        Radius.circular(12f))
                )
        }
        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 30.ipx,
                    modifier = FillColor(Color.Green).clip(shape) + FillColor(Color.Cyan)) {
                }
            }
        }

        takeScreenShot(30).apply {
            // check corners
            assertColor(Color.Cyan, 2, 2)
            assertColor(Color.Green, 2, 27)
            assertColor(Color.Green, 2, 27)
            assertColor(Color.Green, 27, 2)
            // check inner rect
            assertRect(Color.Cyan, size = 18)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun triangleClip() {
        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 30.ipx,
                    modifier = FillColor(Color.Green) + Modifier.clip(triangleShape) +
                            FillColor(Color.Cyan)) {
                }
            }
        }

        takeScreenShot(30).apply {
            assertTriangle(Color.Cyan, Color.Green)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun concaveClip() {
        // 30 pixels rect with a rect hole of 10 pixels in the middle
        val concaveShape = object : Shape {
            override fun createOutline(size: PxSize, density: Density): Outline =
                Outline.Generic(
                    Path().apply {
                        op(
                            Path().apply { addRect(Rect(0f, 0f, 30f, 30f)) },
                            Path().apply { addRect(Rect(10f, 10f, 20f, 20f)) },
                            PathOperation.difference
                        )
                    }
                )
        }
        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 30.ipx,
                    modifier = FillColor(Color.Green) + Modifier.clip(concaveShape) +
                            FillColor(Color.Cyan)) {
                }
            }
        }

        takeScreenShot(30).apply {
            assertRect(color = Color.Green, size = 10)
            assertRect(color = Color.Cyan, size = 30, holeSize = 10)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun switchFromRectToRounded() {
        val model = ValueModel<Shape>(rectShape)

        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 30.ipx,
                    modifier = FillColor(Color.Green) + Modifier.clip(model.value) +
                            FillColor(Color.Cyan)) {
                }
            }
        }

        takeScreenShot(30).apply {
            assertRect(Color.Cyan, size = 30)
        }

        drawLatch = CountDownLatch(1)
        rule.runOnUiThreadIR {
            model.value = object : Shape {
                override fun createOutline(size: PxSize, density: Density): Outline =
                    Outline.Rounded(RRect(size.toRect(), Radius.circular(12f)))
            }
        }

        takeScreenShot(30).apply {
            assertColor(Color.Green, 2, 2)
            assertColor(Color.Green, 2, 27)
            assertColor(Color.Green, 2, 27)
            assertColor(Color.Green, 27, 2)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun switchFromRectToPath() {
        val model = ValueModel<Shape>(rectShape)

        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 30.ipx,
                    modifier = FillColor(Color.Green) + Modifier.clip(model.value) +
                            FillColor(Color.Cyan)) {
                }
            }
        }

        takeScreenShot(30).apply {
            assertRect(Color.Cyan, size = 30)
        }

        drawLatch = CountDownLatch(1)
        rule.runOnUiThreadIR { model.value = triangleShape }

        takeScreenShot(30).apply {
            assertTriangle(Color.Cyan, Color.Green)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun switchFromPathToRect() {
        val model = ValueModel<Shape>(triangleShape)

        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 30.ipx,
                    modifier = FillColor(Color.Green) + Modifier.clip(model.value) +
                            FillColor(Color.Cyan)) {
                }
            }
        }

        takeScreenShot(30).apply {
            assertTriangle(Color.Cyan, Color.Green)
        }

        drawLatch = CountDownLatch(1)
        rule.runOnUiThreadIR { model.value = rectShape }

        takeScreenShot(30).apply {
            assertRect(Color.Cyan, size = 30)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun switchBetweenDifferentPaths() {
        val model = ValueModel<Shape>(triangleShape)
        // to be replaced with a DrawModifier wrapped into remember, so the recomposition
        // is not causing invalidation as the DrawModifier didn't change
        val drawCallback: DrawScope.() -> Unit = {
            drawRect(
                Rect(
                    -100f,
                    -100f,
                    size.width.value + 100f,
                    size.height.value + 100f
                ), Paint().apply {
                    this.color = Color.Cyan
                })
            drawLatch.countDown()
        }

        val clip = object : DrawLayerModifier {
            override val outlineShape: Shape?
                get() = model.value
            override val clipToBounds: Boolean
                get() = true
            override val clipToOutline: Boolean
                get() = true
        }

        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(
                    size = 30.ipx,
                    modifier = background(Color.Green) + clip + Modifier.drawBehind(drawCallback)
                ) {
                }
            }
        }

        takeScreenShot(30).apply {
            assertTriangle(Color.Cyan, Color.Green)
        }

        drawLatch = CountDownLatch(1)
        rule.runOnUiThreadIR { model.value = invertedTriangleShape }

        takeScreenShot(30).apply {
            assertInvertedTriangle(Color.Cyan, Color.Green)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun emitClipLater() {
        val model = ValueModel(false)

        rule.runOnUiThreadIR {
            activity.setContent {
                Padding(size = 10.ipx, modifier = FillColor(Color.Green)) {
                    val modifier = if (model.value) {
                        Modifier.clip(rectShape) + FillColor(Color.Cyan)
                    } else {
                        Modifier
                    }
                    AtLeastSize(size = 10.ipx, modifier = modifier) {
                    }
                }
            }
        }
        Assert.assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        drawLatch = CountDownLatch(1)
        rule.runOnUiThreadIR {
            model.value = true
        }

        takeScreenShot(30).apply {
            assertRect(Color.Cyan, size = 10)
            assertRect(Color.Green, holeSize = 10)
        }
    }

    private fun FillColor(color: Color): Modifier {
        return Modifier.drawBehind {
            drawRect(
                Rect(
                    -100f,
                    -100f,
                    size.width.value + 100f,
                    size.height.value + 100f
                ), Paint().apply {
                    this.color = color
                })
            drawLatch.countDown()
        }
    }

    private fun takeScreenShot(size: Int): Bitmap {
        Assert.assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        val bitmap = rule.waitAndScreenShot()
        Assert.assertEquals(size, bitmap.width)
        Assert.assertEquals(size, bitmap.height)
        return bitmap
    }
}

fun Bitmap.assertTriangle(innerColor: Color, outerColor: Color) {
    Assert.assertEquals(width, height)
    val center = (width - 1) / 2
    val last = width - 1
    // check center
    assertColor(innerColor, center, center)
    // check top corners
    assertColor(outerColor, 4, 4)
    assertColor(outerColor, last - 4, 4)
    // check bottom corners
    assertColor(outerColor, 0, last - 4)
    assertColor(innerColor, 4, last - 4)
    assertColor(outerColor, last, last - 4)
    assertColor(innerColor, last - 4, last)
    // check top center
    assertColor(outerColor, center - 4, 0)
    assertColor(outerColor, center + 4, 0)
    assertColor(innerColor, center, 4)
}

fun Bitmap.assertInvertedTriangle(innerColor: Color, outerColor: Color) {
    Assert.assertEquals(width, height)
    val center = (width - 1) / 2
    val last = width - 1
    // check center
    assertColor(innerColor, center, center)
    // check top corners
    assertColor(outerColor, 0, 4)
    assertColor(innerColor, 4, 4)
    assertColor(outerColor, last, 4)
    assertColor(innerColor, last - 4, 0)
    // check bottom corners
    assertColor(outerColor, 4, last - 4)
    assertColor(outerColor, last - 4, last - 4)
    // check bottom center
    assertColor(outerColor, center - 4, last)
    assertColor(outerColor, center + 4, last)
    assertColor(innerColor, center, last - 4)
}

fun Bitmap.assertColor(expectedColor: Color, x: Int, y: Int) {
    val pixel = Color(getPixel(x, y))
    assertColorsEqual(expectedColor, pixel) {
        "Pixel [$x, $y] is expected to be $expectedColor," + " " + "but was $pixel"
    }
}
