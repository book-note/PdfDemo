package com.merpyzf.pdfdemo

import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.provider.ContactsContract
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Size
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toRectF
import androidx.palette.graphics.Palette
import java.io.File
import java.io.FileOutputStream

/**
 * PDF 内容生成
 * @author WangKe
 */
class MainActivity : AppCompatActivity() {
    private var pageWidth = 0
    private var pageHeight = 0
    private val bookInfoHeightRatio = 3 / 10.0f
    private val bookInfoBgRadius = 14F
    private var margin = 10
    private var startY = 0F
    private lateinit var typeface: Typeface
    private lateinit var document: PdfDocument
    private lateinit var pageInfo: PdfDocument.PageInfo
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas

    private val paintBookInfoBg = Paint()
        .apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = Color.parseColor("#ff504860")
        }
    private val paintImage = Paint().apply {
        isAntiAlias = true
    }
    private val paintText = TextPaint().apply {
        color = Color.BLACK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        typeface = Typeface.createFromAsset(assets, "fonts/SourceHanSerifCN-Medium.otf")
        findViewById<Button>(R.id.btnExportPdf).setOnClickListener {
            exportPdf()
        }
    }

    private fun exportPdf() {
        // region 创建 PDF 文档
        document = PdfDocument()
        pageInfo = PdfDocument.PageInfo.Builder(
            PrintAttributes.MediaSize.ISO_A4.widthMils * 72 / 1000,
            PrintAttributes.MediaSize.ISO_A4.heightMils * 72 / 1000,
            1
        ).create()
        pageWidth = pageInfo.pageWidth
        pageHeight = pageInfo.pageHeight
        page = document.startPage(pageInfo)
        canvas = page.canvas
        // end region

        val book = mockTestData()
        drawBookInfo(book, canvas) {
            drawNoteList(book.noteList)
            document.finishPage(page)
            document.writeTo(
                FileOutputStream(
                    File(
                        Environment.getExternalStorageDirectory(),
                        "《${book.name}》.pdf"
                    )
                )
            )
            document.close()
            Toast.makeText(this, "👌", Toast.LENGTH_SHORT).show()
        }

    }

    private fun drawNoteList(noteList: List<Note>) {
        // 文本绘制的宽度
        val drawTextWidth = (pageInfo.pageWidth - margin * 2 - bookInfoBgRadius * 2).toInt()
        var startX = margin + bookInfoBgRadius
        var lineHeight: Int
        val marginTopOfNote = 10
        val space = 20
        val contentTextColor = Color.parseColor("#1F1F1F")
        val contentTextSize = 14f
        val ideaTextColor = Color.parseColor("#767676")
        val ideaTextSize = 14f
        val noteInfoTextColor = Color.parseColor("#767676")
        val noteInfoTextSize = 12f
        val dividerLineColor = Color.parseColor("#10000000")
        val dividerLineHeight = 1f
        val paintLine = Paint().apply {
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dividerLineHeight
            color = dividerLineColor
        }

        noteList.forEachIndexed { index, note ->
            paintText.color = contentTextColor
            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paintText.textSize = contentTextSize

            // 测量摘录内容高度
            var staticLayout = StaticLayout(
                note.content,
                paintText,
                drawTextWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.6f,
                0f,
                false
            )
            lineHeight = staticLayout.height / staticLayout.lineCount
            // 按行绘制摘录内容
            for (lineIndex in 0 until staticLayout.lineCount) {
                // 在开始绘制每行内容时，先判断是否需要创建新页面
                if (startY + lineHeight + margin > pageInfo.pageHeight) {
                    startNextPage()
                    startY = margin.toFloat()
                }
                startY += lineHeight
                canvas.drawText(
                    getLineText(lineIndex, note.content, staticLayout),
                    startX,
                    startY,
                    paintText
                )
            }
            if (note.idea.isNotBlank()) {
                paintText.color = ideaTextColor
                paintText.textSize = ideaTextSize
                paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                staticLayout = StaticLayout(
                    note.idea,
                    paintText,
                    drawTextWidth,
                    Layout.Alignment.ALIGN_NORMAL,
                    1.6f,
                    0f,
                    false
                )
                startY += marginTopOfNote
                for (lineIndex in 0 until staticLayout.lineCount) {
                    if (startY + lineHeight + margin > pageInfo.pageHeight) {
                        startNextPage()
                        startY = margin.toFloat()
                    }
                    startY += lineHeight
                    canvas.drawText(
                        getLineText(lineIndex, note.idea, staticLayout),
                        startX,
                        startY,
                        paintText
                    )
                }
            }

            val imageCountOfLine = 4
            val imageSpace = 10
            // 目标绘制图像宽度
            val imageWidth =
                (drawTextWidth - ((imageCountOfLine + 1) * imageSpace)) * 1.0f / imageCountOfLine
            var imageHeight: Float
            var maxImageHeightOfLine: Float
            if (note.images.isNotEmpty()) {
                startY += margin
                // 将书摘图片按照每行显示数量进行拆分成二维列表
                val imageGroups = imageGroup(note.images, imageCountOfLine)
                // 绘制每行图片
                imageGroups.forEach { images ->
                    if (imageGroups.isNotEmpty()) {

                        val maxHeightImage = images.maxByOrNull {
                            val size = obtainImageSize(
                                File(
                                    Environment.getExternalStorageDirectory(),
                                    it.image
                                ).path
                            )
                            val scaleRatio = imageWidth * 1.0f / size.width
                            size.height * scaleRatio
                        }

                        val size = obtainImageSize(
                            File(
                                Environment.getExternalStorageDirectory(),
                                maxHeightImage!!.image
                            ).path
                        )

                        // 获取本行最大高度的图片
                        val scaleRatio = imageWidth * 1.0f / size.width
                        val maxImageHeight = pageHeight.toFloat() - margin * 2 - imageSpace * 2
                        maxImageHeightOfLine = size.height * scaleRatio

                        // 处理长图一页绘制不下的情况
                        if (maxImageHeightOfLine > maxImageHeight) {
                            maxImageHeightOfLine = maxImageHeight
                        }

                        if (startY + maxImageHeightOfLine + margin > pageInfo.pageHeight) {
                            startNextPage()
                            startY = margin.toFloat()
                        }

                        startX += imageSpace
                        // 开始绘制图片
                        images.forEach {
                            val bitmap = BitmapFactory.decodeFile(
                                File(
                                    Environment.getExternalStorageDirectory(),
                                    it.image
                                ).path
                            )
                            imageHeight = bitmap.height * (imageWidth * 1.0f / bitmap.width)

                            // 处理长图一页绘制不下的情况
                            if (imageHeight >= maxImageHeight) {
                                imageHeight = maxImageHeight
                            }

                            val matrix = Matrix()
                            matrix.postScale(
                                imageWidth / bitmap.width,
                                imageHeight / bitmap.height
                            )
                            // 一页只绘制一张图片时的位置摆放模式
                            if (imageHeight >= maxImageHeight) {
                                matrix.postTranslate(
                                    startX,
                                    startY + imageSpace
                                )
                            } else {
                                // 正常的图片摆放模式，与最高的图片进行底部对齐
                                matrix.postTranslate(
                                    startX,
                                    startY + (maxImageHeightOfLine - imageHeight) + imageSpace
                                )
                            }
                            canvas.drawBitmap(bitmap, matrix, paintImage)
                            bitmap.recycle()
                            startX += imageSpace + imageWidth
                        }
                        startY += maxImageHeightOfLine + imageSpace
                        startX = margin + bookInfoBgRadius
                    }
                }
            }
            startY += imageSpace + margin

            // 绘制书摘位置信息
            paintText.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            paintText.color = noteInfoTextColor
            paintText.textSize = noteInfoTextSize
            staticLayout = StaticLayout(
                note.position,
                paintText,
                drawTextWidth,
                Layout.Alignment.ALIGN_NORMAL,
                1.6f,
                0f,
                false
            )
            lineHeight = staticLayout.height / staticLayout.lineCount
            for (lineIndex in 0 until staticLayout.lineCount) {
                if (startY + lineHeight + space > pageInfo.pageHeight) {
                    if (startY + lineHeight + margin <= pageInfo.pageHeight) {
                        // 执行此方法
                        // 只绘制位置信息，不绘制下划线
                        canvas.drawText(
                            getLineText(lineIndex, note.position, staticLayout),
                            startX,
                            startY + lineHeight,
                            paintText
                        )
                        continue
                    } else {
                        // 创建新页面，在下一页绘制换行线
                        document.finishPage(page)
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        startY = margin.toFloat()
                    }

                }
                canvas.drawText(
                    getLineText(lineIndex, note.position, staticLayout),
                    startX,
                    startY + lineHeight,
                    paintText
                )
                startY += lineHeight + margin
            }
            // 绘制分割线
            if (index != noteList.size - 1) {
                canvas.drawLine(
                    startX,
                    startY + space / 2,
                    startX + drawTextWidth,
                    startY + space / 2,
                    paintLine
                )
                startY += space
            }
        }
    }

    private fun startNextPage() {
        document.finishPage(page)
        page = document.startPage(pageInfo)
        canvas = page.canvas
    }

    private fun obtainImageSize(path: String): Size {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        return Size(options.outWidth, options.outHeight)
    }

    private fun imageGroup(noteImages: List<Image>, groupLength: Int): List<List<Image>> {
        val imageGroups = mutableListOf<MutableList<Image>>()
        var images = mutableListOf<Image>()
        imageGroups.add(images)
        var groupCount = 1
        noteImages.forEach {
            if ((noteImages.indexOf(it) + 1) % groupLength == 0) {
                images.add(it)
                if (noteImages.size > groupCount * groupLength) {
                    images = mutableListOf()
                    imageGroups.add(images)
                    groupCount++
                }
            } else {
                images.add(it)
            }
        }
        return imageGroups
    }

    private fun getLineText(line: Int, source: String, staticLayout: StaticLayout): String {
        return if (line != staticLayout.lineCount - 1) {
            source.subSequence(
                staticLayout.getLineStart(line),
                staticLayout.getLineStart(line + 1)
            )
        } else {
            source.subSequence(staticLayout.getLineStart(line), source.length)
        }.toString()
    }


    private fun drawBookInfo(book: Book, canvas: Canvas, callback: () -> Unit) {
        val imageHwRatio = 1.4f
        val innerMarginOfBookInfo = 30
        val imageCornerSize = 14f
        val noteCountTextSize = 14f
        val noteCountTextColor = Color.parseColor("#54000000")
        val height = (pageInfo.pageHeight * 1.0f * bookInfoHeightRatio).toInt()
        val bookInfoBackgroundRegion = Region(margin, margin, pageWidth - margin, height)
        val titleTextColor = Color.parseColor("#ffffff")
        val bodyTextColor = Color.parseColor("#ffffff")
        val coverBitmap = BitmapFactory.decodeFile(
            File(
                Environment.getExternalStorageDirectory().path,
                book.cover
            ).toString()
        )
        Palette.from(coverBitmap)
            .generate {
                var bookInfoBgColor = Color.parseColor("#0E0E0E")
                if (it != null && it.darkMutedSwatch != null) {
                    bookInfoBgColor = it.darkMutedSwatch!!.rgb
                }
                paintBookInfoBg.color = bookInfoBgColor
                // 绘制书籍信息背景
                canvas.drawRoundRect(
                    bookInfoBackgroundRegion.bounds.toRectF(),
                    bookInfoBgRadius,
                    bookInfoBgRadius,
                    paintBookInfoBg
                )

                val targetHeight = height - innerMarginOfBookInfo * 2
                val targetWidth = targetHeight / imageHwRatio
                val scaleX = targetWidth * 1.0f / coverBitmap.width
                val scaleY = targetHeight * 1.0f / coverBitmap.height
                val translateX = margin + innerMarginOfBookInfo.toFloat()
                val translateY = margin / 2f + (height - targetHeight).toFloat() / 2

                // region 绘制书籍封面图片
                val layerId = canvas.saveLayer(
                    0f,
                    0f,
                    bookInfoBackgroundRegion.bounds.right.toFloat(),
                    bookInfoBackgroundRegion.bounds.bottom.toFloat(),
                    paintImage
                )
                val matrix = Matrix()
                matrix.postScale(scaleX, scaleY)
                matrix.postTranslate(translateX, translateY)
                val rectF = RectF(
                    translateX,
                    translateY,
                    translateX + targetWidth,
                    translateY + targetHeight
                )
                canvas.drawRoundRect(rectF, imageCornerSize, imageCornerSize, paintImage)
                paintImage.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
                canvas.drawBitmap(coverBitmap, matrix, paintImage)
                canvas.restoreToCount(layerId)
                coverBitmap.recycle()
                paintImage.xfermode = null
                // end region

                // region 绘制书籍信息
                val marginTopOfTitle = 24f
                var bookInfoRegionTotalHeight = 0 + marginTopOfTitle
                val textMargin = 4
                val titleSize = 24f
                val bodySize = 16f
                // 定义书籍信息可绘制区域
                val bookInfoRegion = Region(
                    (margin + innerMarginOfBookInfo + targetWidth + innerMarginOfBookInfo).toInt(),
                    margin + innerMarginOfBookInfo,
                    pageWidth - innerMarginOfBookInfo - margin,
                    margin + height
                )

                val bookInfoFields = mutableListOf(
                    book.name,
                    "作者：${book.author}",
                    if (book.translator.isBlank()) {
                        ""
                    } else {
                        "译者：${book.translator}"
                    },
                    "出版社：${book.press}",
                    "出版年：${book.pubDate}",
                    "ISBN: ${book.isbn}"
                )
                val staticLayouts = mutableListOf<StaticLayout>()

                // 对待绘制的书籍信息文本进行测量
                bookInfoFields.forEachIndexed { index, s ->
                    if (index == 0) {
                        paintText.typeface = typeface
                        paintText.textSize = titleSize
                        paintText.color = titleTextColor
                    } else {
                        paintText.textSize = bodySize
                        paintText.typeface = null
                    }
                    val staticLayout = StaticLayout(
                        s,
                        paintText,
                        bookInfoRegion.bounds.width(),
                        Layout.Alignment.ALIGN_NORMAL,
                        0f, 0f, false
                    )
                    if (index == 0 || index == 1) {
                        bookInfoRegionTotalHeight += staticLayout.height
                    } else {
                        // 如果译者字段为空字符串则不测量其高度
                        if (s.isNotBlank()) {
                            bookInfoRegionTotalHeight += staticLayout.height + textMargin
                        }
                    }
                    staticLayouts.add(staticLayout)
                }

                // 计算能够让书籍信息居中显示的纵轴起点坐标
                startY =
                    (bookInfoRegion.bounds.height() - bookInfoRegionTotalHeight - staticLayouts[0].height - staticLayouts[staticLayouts.size - 1].height * 2) * 1.0f / 2 + bookInfoRegion.bounds.top
                var startX = bookInfoRegion.bounds.left.toFloat()

                // 循环绘制书籍信息
                bookInfoFields.forEachIndexed { index, s ->
                    if (index == 0) {
                        paintText.typeface = typeface
                        paintText.textSize = titleSize
                        paintText.color = titleTextColor
                    } else {
                        paintText.textSize = bodySize
                        paintText.color = bodyTextColor
                        paintText.typeface = null
                    }
                    if (s.isNotBlank()) {
                        val staticLayout = staticLayouts[index]
                        val lineHeight = staticLayout.height.toFloat() / staticLayout.lineCount
                        if (index == 1) {
                            startY += marginTopOfTitle
                        } else {
                            startY += textMargin
                        }
                        for (lineIndex in 0 until staticLayout.lineCount) {
                            canvas.drawText(
                                getLineText(lineIndex, s, staticLayout),
                                startX,
                                startY + lineHeight,
                                paintText
                            )
                            startY += lineHeight
                        }
                    }
                }
                // region 绘制书摘数目信息
                paintText.textSize = noteCountTextSize
                paintText.color = noteCountTextColor
                val noteCountInfo = "共 ${book.noteList.size} 条书摘"
                val noteCountLayout = StaticLayout(
                    noteCountInfo,
                    paintText,
                    (pageWidth - margin * 2 - bookInfoBgRadius * 2).toInt(),
                    Layout.Alignment.ALIGN_NORMAL,
                    0f,
                    paintText.textSize,
                    false
                )
                startY = bookInfoRegion.bounds.bottom.toFloat() + noteCountLayout.height
                startX = margin + bookInfoBgRadius
                canvas.drawText(noteCountInfo, startX, startY, paintText)
                startY += noteCountLayout.height
                // end region
                callback()
            }
        // end region
    }

    private fun mockTestData(): Book {
        val noteList = mutableListOf<Note>()
        val note0 = Note(
            content = "我告诉你这么多有关B612号小行星的事情，让你知道它的编号，是因为大人。大人热爱数字。如果你跟他们说你认识了新朋友，他们从来不会问你重要的事情。他们从来不会说：“他的声音听起来怎么样？他最喜欢什么游戏？他收集蝴蝶吗？”他们会问：“他多少岁？有多少个兄弟？他有多重？他父亲赚多少钱？”只有这样他们才会觉得他们了解了他。如果你对大人说：“我看到一座漂亮红砖房，窗台上摆着几盆天竺葵，屋顶有许多鸽子……”那他们想象不出这座房子是什么样的。你必须说：“我看到一座价值十万法郎的房子。”他们就会惊叫：“哇，多漂亮的房子啊！”",
            idea = "",
            mutableListOf(),
            position = "2020-05-18 ｜ Chapter 04"
        )
        val note1 = Note(
            content = "“每天早晨洗漱好以后，你必须仔细地清洁和打扮你的星球。你必须强迫自己经常去拔掉猴面包树，它小时候跟玫瑰的幼苗长得很像，你要是能把它认出来，马上就得拔掉。这是非常乏味的劳动，但也非常简单。”",
            idea = "",
            mutableListOf(
                Image("测试8.jpeg"),
                Image("测试9.jpeg"),
                Image("测试10.jpg"),
                Image("测试12.jpg"),
            ),
            position = "2020-05-18 ｜ Chapter 05"
        )

        val note2 = Note(
            content = "我发现了小王子生命中的秘密。当时他突然向我发问，事先没有任何征兆，仿佛这个问题他已经默默思考了很久。 “既然绵羊会吃矮小的灌木，那么它也吃花朵吗？” “绵羊看见什么就吃什么。” “连有刺的花也吃吗？” “是啊。连有刺的花也吃。” “那么，那些刺有什么用呢？”",
            idea = "",
            mutableListOf(),
            position = "2020-05-18 ｜ Chapter 07"
        )

        val note3 = Note(
            content = "狐狸说，“对我来说，你无非是个孩子，和其他成千上万个孩子没有什么区别。我不需要你。你也不需要我。对你来说，我无非是只狐狸，和其他成千上万只狐狸没有什么不同。但如果你驯化了我，那我们就会彼此需要。你对我来说是独一无二的，我对你来说也是独一无二的……”",
            idea = "",
            mutableListOf(),
            position = "2020-05-18 ｜ Chapter 21"
        )
        val note4 = Note(
            content = "“我的生活很单调。我猎杀鸡，人猎杀我。所有的鸡都是相同的，所有的人也是相同的。我已经有点厌倦。但如果你驯化我，我的生活将会充满阳光。我将能够辨别一种与众不同的脚步声。别人的脚步声会让我躲到地下。而你的脚步声就像音乐般美好，会让我走出洞穴。还有，你看。你看到那片麦田吗？我不吃面包。小麦对我来说没有用。麦田不会让我想起什么。这是很悲哀的！但你的头发是金色的。所以你来驯化我是很美好的事情！小麦也是金色的，到时它将会让我想起你。我喜欢风吹过麦穗的声音……”",
            idea = "",
            mutableListOf(
                Image("测试1.jpeg"),
                Image("测试2.jpeg"),
                Image("测试3.jpeg"),
                Image("测试4.jpeg"),
                Image("测试6.jpeg"),
                Image("测试7.jpeg")
            ),
            position = "2020-05-18 ｜ Chapter 21"
        )
        val note5 = Note(
            content = "但我很担心，我想起了狐狸：如果让自己被驯化，就难免会流泪……",
            idea = "如果你想跟别人制造羁绊，就要承受流泪的风险",
            mutableListOf(
                Image("测试8.jpeg"),
                Image("测试9.jpeg"),
                Image("测试10.jpg"),
                Image("测试11.jpg"),
                Image("测试12.jpg")
            ),
            position = "2020-05-18 ｜ Chapter 25"
        )

        noteList.add(note0)
        noteList.add(note1)
        noteList.add(note2)
        noteList.add(note3)
        noteList.add(note4)
        noteList.add(note5)

        return Book(
            "小王子.jpeg",
            "小王子",
            "安东尼·德·圣-埃克苏佩里",
            "树才",
            "浙江文艺出版社",
            "2017-3-1",
            "9787533947279",
            noteList
        )
    }


    data class Book(
        var cover: String,
        var name: String,
        var author: String,
        var translator: String,
        var press: String,
        var pubDate: String,
        var isbn: String,
        var noteList: List<Note>
    )

    data class Note(
        var content: String,
        var idea: String,
        var images: List<Image>,
        var position: String
    )

    data class Image(var image: String)
}