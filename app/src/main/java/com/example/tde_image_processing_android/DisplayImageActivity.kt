package com.example.tde_image_processing_android

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.tde_image_processing_android.databinding.ActivityDisplayImageBinding
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale


class DisplayImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDisplayImageBinding

    private var graySeekBarProgress: Int = 0
    private var brightnessSeekBarProgress: Int = 0
    private var contrastSeekBarProgress: Int = 0
    private var sepiaSeekBarProgress: Int = 0
    private var isNegativeColorFilter: Boolean = false

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityDisplayImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        handleReceivedImageURI()

        setupGrayScaleControl()
        setupBrightnessControl()
        setupContrastControl()
        setupSepiaControl()
        setupNegativeControl()

        setupOnSaveButtonClickListener()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupOnSaveButtonClickListener() {
        binding.button.setOnClickListener {
            val imageUri = intent.getStringExtra("image_uri")?.let { Uri.parse(it) }
            imageUri?.let { it ->
                val filteredBitmapImage = applyFiltersToBitmapFromUri(it)
                binding.imageView.setImageBitmap(filteredBitmapImage)

                val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "CameraX-Image-Filtered-$name")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image-Filtered")
                }

                val contentResolver = contentResolver

                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                uri?.let {
                    val outputStream = contentResolver.openOutputStream(it)
                    if (outputStream != null) {
                        filteredBitmapImage?.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    }
                    outputStream?.close()
                    Toast.makeText(this, "Image saved", Toast.LENGTH_SHORT).show()
                } ?: Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
            }

        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun applyFiltersToBitmapFromUri(imageUri: Uri): Bitmap? {
        val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        originalBitmap ?: return null

        // Obter a orientação correta da imagem
        val rotation = getBitmapRotation(imageUri)
        val rotatedBitmap = if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            val rotated = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
            if (!originalBitmap.isRecycled) originalBitmap.recycle()
            rotated
        } else {
            originalBitmap
        }

        val colorMatrix = ColorMatrix()
        if (graySeekBarProgress > 0) colorMatrix.postConcat(getGrayScaleColorMatrix())
        if (brightnessSeekBarProgress > 0) colorMatrix.postConcat(getBrightnessColorMatrix())
        if (contrastSeekBarProgress > 0) colorMatrix.postConcat(getContrastColorMatrix())
        if (sepiaSeekBarProgress > 0) colorMatrix.postConcat(getSepiaColorMatrix())
        if (isNegativeColorFilter) colorMatrix.postConcat(getNegativeColorMatrix())

        val filteredBitmap = Bitmap.createBitmap(
            rotatedBitmap.width,
            rotatedBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(filteredBitmap)
        val paint = Paint()
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(rotatedBitmap, 0f, 0f, paint)

        if (!rotatedBitmap.isRecycled) {
            rotatedBitmap.recycle()
        }

        return filteredBitmap
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun getBitmapRotation(imageUri: Uri): Int {
        val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
        val exif = inputStream?.let { ExifInterface(it) }
        val orientation =
            exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        inputStream?.close()

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }

    private fun handleReceivedImageURI() {
        val imageUri = intent.getStringExtra("image_uri")?.let { Uri.parse(it) }

        imageUri?.let {
            binding.imageView.setImageURI(it)
        } ?: Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()

    }

    private fun setupGrayScaleControl() {
        binding.seekBarGrayScale.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                graySeekBarProgress = progress
                applyFiltersOnImageView()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }

    private fun setupBrightnessControl() {
        binding.seekBarBrightness.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brightnessSeekBarProgress = progress
                applyFiltersOnImageView()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }

    private fun setupContrastControl() {
        binding.contrastControl.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                contrastSeekBarProgress = progress
                applyFiltersOnImageView()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }

    private fun setupSepiaControl() {
        binding.sepiaControl.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sepiaSeekBarProgress = progress
                applyFiltersOnImageView()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
            }
        })
    }

    private fun setupNegativeControl() {
        binding.negativeSwitch.setOnCheckedChangeListener { _, isChecked ->
            isNegativeColorFilter = isChecked
            applyFiltersOnImageView()
        }
    }

    private fun applyFiltersOnImageView() {
        val colorMatrix = ColorMatrix()

        if (graySeekBarProgress > 0) colorMatrix.postConcat(getGrayScaleColorMatrix())

        if (brightnessSeekBarProgress > 0) colorMatrix.postConcat(getBrightnessColorMatrix())

        if (contrastSeekBarProgress > 0) colorMatrix.postConcat(getContrastColorMatrix())

        if (sepiaSeekBarProgress > 0) colorMatrix.postConcat(getSepiaColorMatrix())

        if (isNegativeColorFilter) colorMatrix.postConcat(getNegativeColorMatrix())

        binding.imageView.colorFilter = ColorMatrixColorFilter(colorMatrix)
    }

    private fun getGrayScaleColorMatrix(): ColorMatrix {
        val scale = graySeekBarProgress / 100f
        val saturation = 1 - scale
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(saturation)
        return colorMatrix
    }

    private fun getBrightnessColorMatrix(): ColorMatrix {
        val scale =
            brightnessSeekBarProgress / 100f * 2  // Converte de 0 a 100 para uma escala de 0 a 2

        return ColorMatrix(
            floatArrayOf(
                scale, 0.0f, 0.0f, 0.0f, 0.0f,  // vermelho
                0.0f, scale, 0.0f, 0.0f, 0.0f, // verde
                0.0f, 0.0f, scale, 0.0f, 0.0f, // azul
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f   // alfa
            )
        )
    }

    private fun getContrastColorMatrix(): ColorMatrix {
        val contrast = contrastSeekBarProgress / 100f * 1.9f + 0.1f
        val translation = 128f * (1f - contrast)

        return ColorMatrix(
            floatArrayOf(
                contrast, 0.0f, 0.0f, 0.0f, translation,
                0.0f, contrast, 0.0f, 0.0f, translation,
                0.0f, 0.0f, contrast, 0.0f, translation,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            )
        )
    }

    private fun getSepiaColorMatrix(): ColorMatrix {
        val scale = sepiaSeekBarProgress / 100f
        val colorMatrix = ColorMatrix()
        colorMatrix.set(
            floatArrayOf(
                0.393f + 0.607f * (1 - scale),
                0.769f - 0.769f * (1 - scale),
                0.189f - 0.189f * (1 - scale),
                0f,
                0f,
                0.349f - 0.349f * (1 - scale),
                0.686f + 0.314f * (1 - scale),
                0.168f - 0.168f * (1 - scale),
                0f,
                0f,
                0.272f - 0.272f * (1 - scale),
                0.534f - 0.534f * (1 - scale),
                0.131f + 0.869f * (1 - scale),
                0f,
                0f,
                0f,
                0f,
                0f,
                1f,
                0f
            )
        )
        return colorMatrix
    }

    private fun getNegativeColorMatrix(): ColorMatrix {
        return ColorMatrix(
            floatArrayOf(
                -1.0f,
                0.0f,
                0.0f,
                0.0f,
                255.0f,
                0.0f,
                -1.0f,
                0.0f,
                0.0f,
                255.0f,
                0.0f,
                0.0f,
                -1.0f,
                0.0f,
                255.0f,
                0.0f,
                0.0f,
                0.0f,
                1.0f,
                0.0f
            )
        )
    }
}