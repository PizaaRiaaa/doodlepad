package com.simplemobiletools.draw.pro.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.PERMISSION_WRITE_STORAGE
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.models.FileDirItem
import com.simplemobiletools.draw.pro.BuildConfig
import com.simplemobiletools.draw.pro.R
import com.simplemobiletools.draw.pro.dialogs.SaveImageDialog
import com.simplemobiletools.draw.pro.extensions.config
import com.simplemobiletools.draw.pro.helpers.PNG
import com.simplemobiletools.draw.pro.helpers.SVG
import com.simplemobiletools.draw.pro.models.Svg
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

class LandingActivity: SimpleActivity() {
    private val PICK_IMAGE_INTENT = 1
    private val SAVE_IMAGE_INTENT = 2

    private val FOLDER_NAME = "images"
    private val FILE_NAME = "simple-draw.png"

    private var defaultPath = ""
    private var defaultFilename = ""

    private var defaultExtension = PNG
    private var intentUri: Uri? = null
    private var savedPathsHash = 0L
    private var isEditIntent = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_landing)
        supportActionBar?.hide();

        val btnCreateNew: Button = findViewById(R.id.btnCreateNew)
        val btnChoosefromGallery: Button = findViewById(R.id.btnChooseFromGallery)

        //MainActivity(Canvas) will pop-up when btnCreateNew was clicked
        btnCreateNew.setOnClickListener{
            intent = Intent(this,MainActivity::class.java)
            startActivity(intent)
        }

        btnChoosefromGallery.setOnClickListener{
            tryOpenFile()
        }
    }




    //open gallery when btnChooseFromGallery was clicked
    private fun tryOpenFile() {
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            try {
                startActivityForResult(this, PICK_IMAGE_INTENT)
            } catch (e: ActivityNotFoundException) {
                toast(R.string.no_app_found)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun confirmImage() {
        when {
            isEditIntent -> {
                try {
                    val outputStream = contentResolver.openOutputStream(intentUri!!)
                    saveToOutputStream(outputStream, defaultPath.getCompressionFormat(), true)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
            intentUri?.scheme == "content" -> {
                val outputStream = contentResolver.openOutputStream(intentUri!!)
                saveToOutputStream(outputStream, defaultPath.getCompressionFormat(), true)
            }
            else -> handlePermission(PERMISSION_WRITE_STORAGE) {
                val fileDirItem = FileDirItem(defaultPath, defaultPath.getFilenameFromPath())
                getFileOutputStream(fileDirItem, true) {
                    saveToOutputStream(it, defaultPath.getCompressionFormat(), true)
                }
            }
        }
    }

    private fun saveToOutputStream(outputStream: OutputStream?, format: Bitmap.CompressFormat, finishAfterSaving: Boolean) {
        if (outputStream == null) {
            toast(R.string.unknown_error_occurred)
            return
        }

        val quality = if (format == Bitmap.CompressFormat.PNG) {
            100
        } else {
            70
        }

        outputStream.use {
            my_canvas.getBitmap().compress(format, quality, it)
        }

        if (finishAfterSaving) {
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun trySaveImage() {
        if (isQPlus()) {
            SaveImageDialog(this, defaultPath, defaultFilename, defaultExtension, true) { fullPath, filename, extension ->
                val mimetype = if (extension == SVG) "svg+xml" else extension

                defaultFilename = filename
                defaultExtension = extension
                config.lastSaveExtension = extension

                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = "image/$mimetype"
                    putExtra(Intent.EXTRA_TITLE, "$filename.$extension")
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, SAVE_IMAGE_INTENT)
                    } catch (e: ActivityNotFoundException) {
                        toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        } else {
            getStoragePermission {
                saveImage()
            }
        }
    }
    private fun getStoragePermission(callback: () -> Unit) {
        handlePermission(PERMISSION_WRITE_STORAGE) {
            if (it) {
                callback()
            } else {
                toast(R.string.no_storage_permissions)
            }
        }
    }


    private fun saveImage() {
        SaveImageDialog(this, defaultPath, defaultFilename, defaultExtension, false) { fullPath, filename, extension ->
            savedPathsHash = my_canvas.getDrawingHashCode()
            saveFile(fullPath)
            defaultPath = fullPath.getParentPath()
            defaultFilename = filename
            defaultExtension = extension
            config.lastSaveFolder = defaultPath
            config.lastSaveExtension = extension
        }
    }

    private fun saveFile(path: String) {
        when (path.getFilenameExtension()) {
            SVG -> Svg.saveSvg(this, path, my_canvas)
            else -> saveImageFile(path)
        }
        rescanPaths(arrayListOf(path)) {}
    }

    private fun saveImageFile(path: String) {
        val fileDirItem = FileDirItem(path, path.getFilenameFromPath())
        getFileOutputStream(fileDirItem, true) {
            if (it != null) {
                writeToOutputStream(path, it)
                toast(R.string.file_saved)
            } else {
                toast(R.string.unknown_error_occurred)
            }
        }
    }

    private fun writeToOutputStream(path: String, out: OutputStream) {
        out.use {
            my_canvas.getBitmap().compress(path.getCompressionFormat(), 70, out)
        }
    }

    private fun shareImage() {
        getImagePath(my_canvas.getBitmap()) {
            if (it != null) {
                sharePathIntent(it, BuildConfig.APPLICATION_ID)
            } else {
                toast(R.string.unknown_error_occurred)
            }
        }
    }

    private fun getImagePath(bitmap: Bitmap, callback: (path: String?) -> Unit) {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, bytes)

        val folder = File(cacheDir, FOLDER_NAME)
        if (!folder.exists()) {
            if (!folder.mkdir()) {
                callback(null)
                return
            }
        }

        val newPath = "$folder/$FILE_NAME"
        val fileDirItem = FileDirItem(newPath, FILE_NAME)
        getFileOutputStream(fileDirItem, true) {
            if (it != null) {
                try {
                    it.write(bytes.toByteArray())
                    callback(newPath)
                } catch (e: Exception) {
                } finally {
                    it.close()
                }
            } else {
                callback("")
            }
        }
    }
}

