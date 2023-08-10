/*
 *  Copyright (c) 2018, Jaisel Rahman <jaiselrahman@gmail.com>.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.jaiselrahman.filepickersample

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.jaiselrahman.filepicker.activity.DirSelectActivity
import com.jaiselrahman.filepicker.activity.FilePickerActivity
import com.jaiselrahman.filepicker.activity.PickFile
import com.jaiselrahman.filepicker.config.Configurations
import com.jaiselrahman.filepicker.model.MediaFile
import com.jaiselrahman.filepicker.utils.FilePickerProvider
import java.io.File
import java.io.IOException
import java.util.jar.Manifest

class MainActivity : AppCompatActivity() {
    private var fileListAdapter: FileListAdapter? = null
    private val mediaFiles = ArrayList<MediaFile>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val recyclerView = findViewById<RecyclerView>(R.id.file_list)
        fileListAdapter = FileListAdapter(mediaFiles)
        recyclerView.adapter = fileListAdapter
        val pickImage = registerForActivityResult(PickFile().throughDir(true)) { result ->
            if (result != null) setMediaFiles(result) else Toast.makeText(
                this@MainActivity,
                "Image not selected",
                Toast.LENGTH_SHORT
            ).show()
        }

        val requestVideo = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (it.filter { !it.value }.isEmpty()) {
                val intent = Intent(this@MainActivity, FilePickerActivity::class.java)
                intent.putExtra(
                    FilePickerActivity.CONFIGS, Configurations.Builder()
                        .setCheckPermission(false)
                        .setSelectedMediaFiles(mediaFiles)
                        .enableVideoCapture(true)
                        .setShowImages(false)
                        .setMaxSelection(10)
                        .setIgnorePaths(".*WhatsApp.*")
                        .build()
                )
                startActivityForResult(intent, FILE_REQUEST_CODE)
            } else {
                Toast.makeText(this@MainActivity, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }


        findViewById<View>(R.id.launch_imagePicker).setOnClickListener {
            pickImage.launch(
                Configurations.Builder()
                    .setCheckPermission(true)
                    .setSelectedMediaFiles(mediaFiles)
                    .enableImageCapture(true)
                    .setShowVideos(false)
                    .setSkipZeroSizeFiles(true)
                    .setMaxSelection(10)
                    .build()
            )
        }
        findViewById<View>(R.id.launch_videoPicker).setOnClickListener {
            requestVideo.launch(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(android.Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }.toTypedArray())
        }

        findViewById<View>(R.id.launch_audioPicker).setOnClickListener {
            val intent = Intent(this@MainActivity, FilePickerActivity::class.java)
            var file: MediaFile? = null
            for (i in mediaFiles.indices) {
                if (mediaFiles[i].mediaType == MediaFile.TYPE_AUDIO) {
                    file = mediaFiles[i]
                }
            }
            intent.putExtra(
                FilePickerActivity.CONFIGS, Configurations.Builder()
                    .setCheckPermission(true)
                    .setShowImages(false)
                    .setShowVideos(false)
                    .setIgnoreHiddenFile(false)
                    .setIgnoreNoMedia(false)
                    .setShowAudios(true)
                    .setSingleChoiceMode(true)
                    .setSelectedMediaFile(file)
                    .setTitle("Select an audio")
                    .build()
            )
            startActivityForResult(intent, FILE_REQUEST_CODE)
        }
        findViewById<View>(R.id.launch_filePicker).setOnClickListener {
            val intent = Intent(this@MainActivity, DirSelectActivity::class.java)
            intent.putExtra(
                DirSelectActivity.CONFIGS, Configurations.Builder()
                    .setCheckPermission(true)
                    .setSelectedMediaFiles(mediaFiles)
                    .setShowFiles(true)
                    .setShowImages(true)
                    .setShowAudios(true)
                    .setShowVideos(true)
                    .setIgnoreNoMedia(false)
                    .enableVideoCapture(true)
                    .enableImageCapture(true)
                    .setIgnoreHiddenFile(false)
                    .setMaxSelection(10)
                    .setTitle("Select a file")
                    .build()
            )
            startActivityForResult(intent, FILE_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val mediaFiles: List<MediaFile>? =
                data.getParcelableArrayListExtra(FilePickerActivity.MEDIA_FILES)
            if (mediaFiles != null) {
                setMediaFiles(mediaFiles)
            } else {
                Toast.makeText(this@MainActivity, "Image not selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setMediaFiles(mediaFiles: List<MediaFile>) {
        this.mediaFiles.clear()
        this.mediaFiles.addAll(mediaFiles)
        fileListAdapter!!.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.share_log) {
            shareLogFile()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    fun shareLogFile() {
        val logFile = File(externalCacheDir, "logcat.txt")
        try {
            if (logFile.exists()) logFile.delete()
            logFile.createNewFile()
            Runtime.getRuntime()
                .exec("logcat -f " + logFile.absolutePath + " -t 100 *:W Glide:S " + FilePickerActivity.TAG)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (logFile.exists()) {
            val intentShareFile = Intent(Intent.ACTION_SEND)
            intentShareFile.type = "text/plain"
            intentShareFile.putExtra(
                Intent.EXTRA_STREAM,
                FilePickerProvider.getUriForFile(this, logFile)
            )
            intentShareFile.putExtra(Intent.EXTRA_SUBJECT, "FilePicker Log File")
            intentShareFile.putExtra(Intent.EXTRA_TEXT, "FilePicker Log File")
            startActivity(Intent.createChooser(intentShareFile, "Share"))
        }
    }

    companion object {
        private const val FILE_REQUEST_CODE = 1
    }
}