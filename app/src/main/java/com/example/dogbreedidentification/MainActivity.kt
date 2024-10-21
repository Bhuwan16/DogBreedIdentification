package com.example.dogbreedidentification

import android.R.attr
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.service.controls.templates.ThumbnailTemplate
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.dogbreedidentification.ml.Tfmodel
import kotlinx.android.synthetic.main.activity_main.*
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.jar.Manifest
import kotlin.math.min
import com.example.dogbreedidentification.R
import android.graphics.Matrix

import android.media.ExifInterface
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.example.dogbreedidentification.ml.Preprocess
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import java.lang.Exception
import android.provider.MediaStore.Images
import java.io.ByteArrayOutputStream
import android.R.attr.path
import android.R.attr.bitmap
import android.R.attr.bitmap











class MainActivity : AppCompatActivity() {
    private val context = this
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("size",dogs.size.toString())
        take_picture.setOnClickListener {
            if(checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                startActivityForResult(cameraIntent,1)
            }
            else {
                val permissionList = arrayOf<String>(android.Manifest.permission.CAMERA)
                requestPermissions(permissionList, 100)
            }
        }

        open_gallery.setOnClickListener {
            val galleryIntent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent,2)
        }
    }

    fun classifyImage(image:Bitmap){
        try {
            val model = Preprocess.newInstance(context)

            // Creates inputs for reference.
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 331, 331, 3), DataType.FLOAT32)

            var byteBuffer = ByteBuffer.allocateDirect(4*331*331*3)
            byteBuffer.order(ByteOrder.nativeOrder())

            var intValues = IntArray(331*331)
            image.getPixels(intValues,0,image.width,0,0,image.width,image.height)

            var pixel = 0
            var imageProcessor = ImageProcessor.Builder().add(ResizeOp(331,331,ResizeOp.ResizeMethod.BILINEAR)).build()
            var buffer = imageProcessor.process(TensorImage(DataType.FLOAT32).apply { load(image) })

            val model2 = Tfmodel.newInstance(context)


            // Runs model inference and gets result.
            val outputs = model.process(buffer.tensorBuffer)

            var finalOutput = model2.process(outputs.outputFeature0AsTensorBuffer)

            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            //Log.d("OutputFeatures",outputs.toString())
            var conf: FloatArray = finalOutput.outputFeature0AsTensorBuffer.floatArray
            //Log.d("confconf",conf.size.toString())
            var maxPos = 0
            var maxV = -1.0f
            for(i in 0..119){
                if(conf[i]>maxV){
                    maxPos = i
                    maxV = conf[i]
                }
            }
            dog_name.text = dogs[maxPos]
            // Releases model resources if no longer used.
            model.close()
        }
        catch (e:IOException){

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if(resultCode == RESULT_OK){
            if(requestCode == 1){
                var image:Bitmap = data!!.extras!!.get("data") as Bitmap
                image = RotateBitmap(image,90.0f)
                var dimension = min(image.width,image.height)
                image = ThumbnailUtils.extractThumbnail(image,dimension,dimension)
                image_view.setImageBitmap(image)
                image = Bitmap.createScaledBitmap(image,331,331,false)
                classifyImage(image)
            }
            else{
                var dat = data!!.data
                var image:Bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver,dat)
                try {
                    image = MediaStore.Images.Media.getBitmap(context.contentResolver,dat)
                }
                catch (e:IOException){
                    e.printStackTrace()
                }

                image_view.setImageBitmap(image)
                image = Bitmap.createScaledBitmap(image,331,331,false)
                classifyImage(image)
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun RotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun getImageUri(inContext: Context, inImage: Bitmap): Uri? {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = Images.Media.insertImage(inContext.contentResolver, inImage, "Title", null)
        return Uri.parse(path)
    }

    fun getRealPathFromURI(uri: Uri?): String? {
        var path = ""
        if (contentResolver != null) {
            val cursor: Cursor? = contentResolver.query(uri!!, null, null, null, null)
            if (cursor != null) {
                cursor.moveToFirst()
                val idx: Int = cursor.getColumnIndex(Images.ImageColumns.DATA)
                path = cursor.getString(idx)
                cursor.close()
            }
        }
        return path
    }

    fun rotateImage(bitmap: Bitmap,path:String): Bitmap {
        var exif: ExifInterface? = null
        try {
            exif = ExifInterface(path)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val orientation = exif!!.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> return bitmap
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            val bmRotated: Bitmap = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
            bitmap.recycle()
            bmRotated
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
            bitmap
        }
    }
}