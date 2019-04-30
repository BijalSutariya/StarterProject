package com.example.starterproject

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.Toast
import com.esri.arcgisruntime.concurrent.Job
import com.esri.arcgisruntime.geometry.Envelope
import com.esri.arcgisruntime.loadable.LoadStatus
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.view.Graphic
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.portal.Portal
import com.esri.arcgisruntime.portal.PortalItem
import com.esri.arcgisruntime.security.AuthenticationManager
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler
import com.esri.arcgisruntime.symbology.SimpleLineSymbol
import com.esri.arcgisruntime.tasks.offlinemap.GenerateOfflineMapParameters
import com.esri.arcgisruntime.tasks.offlinemap.OfflineMapTask
import java.io.File


@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    lateinit var downloadArea: Graphic
    lateinit var mGraphicsOverlay: GraphicsOverlay
    lateinit var map: ArcGISMap

    private lateinit var mapView: MapView
    private lateinit var btnSave: Button
    private lateinit var btnShow: Button

    private fun searchForFile(file: File, fileName: String): File? {
        if (file.isDirectory()) {
            val arr = file.listFiles()
            for (f in arr) {
                val found = searchForFile(f, fileName)
                if (found != null)
                    return found
            }
        } else {
            if (file.name == fileName) {
                return file
            }
        }
        return null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSave = this.findViewById(R.id.btnSave)
        btnShow = this.findViewById(R.id.btnShow)
        mapView = this.findViewById(R.id.arcgisMapview)

        btnSave.isEnabled = false

        requestWritePermission();

        AuthenticationManager.setAuthenticationChallengeHandler(DefaultAuthenticationChallengeHandler(this));

        val portal = Portal(getString(R.string.portal_url), false)
        val portalItem = PortalItem(portal, getString(R.string.item_id))

        // create a map with the portal item
        map = ArcGISMap(portalItem)
        map.addDoneLoadingListener {
            if (map.loadStatus == LoadStatus.LOADED) {
                btnSave.isEnabled = true
                // limit the map scale to the largest layer scale
                map.maxScale = map.operationalLayers[6].maxScale;
                map.minScale = map.operationalLayers[6].minScale;
            } else {
                val error = "Map failed to load: " + map.loadError.additionalMessage
                Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                Log.e("TAG", error);
            }
        }
        // set the map to the map view
        mapView.map = map

        // create a graphics overlay for the map view
        mGraphicsOverlay = GraphicsOverlay()
        mapView.graphicsOverlays.add(mGraphicsOverlay)

        // create a graphic to show a box around the extent we want to download
        downloadArea = Graphic()
        mGraphicsOverlay.graphics.add(downloadArea)
        val simpleLineSymbol = SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 2F)
        downloadArea.symbol = simpleLineSymbol


        // update the download area box whenever the viewpoint changes
        mapView.addViewpointChangedListener {
            if (map.loadStatus == LoadStatus.LOADED) {
                // upper left corner of the area to take offline
                val minScreenPoint = android.graphics.Point(200, 200)
                // lower right corner of the downloaded area
                val maxScreenPoint = android.graphics.Point(mapView.width - 200, mapView.height - 200)
                // convert screen points to map points
                val minPoint = mapView.screenToLocation(minScreenPoint)
                val maxPoint = mapView.screenToLocation(maxScreenPoint)

                // use the points to define and return an envelope
                if (minPoint != null && maxPoint != null) {
                    val envelope = Envelope(minPoint, maxPoint)
                    downloadArea.geometry = envelope
                }

            }
        }
        // setUpMethod()

    }

    private fun requestWritePermission() {
        // request write permission
        val reqPermission = arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val requestCode = 2
        if (ContextCompat.checkSelfPermission(this, reqPermission[0]) == PackageManager.PERMISSION_GRANTED) {
            generateOfflineMap();
        } else {
            // request permission
            ActivityCompat.requestPermissions(this, reqPermission, requestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            generateOfflineMap();
            Log.d("TAG", "permission granted");
        } else {
            // report to user that permission was denied
            Toast.makeText(this, getString(R.string.offline_map_write_permission_denied), Toast.LENGTH_SHORT).show();
        }
    }

    private fun generateOfflineMap() {
        // create a progress dialog to show download progress
        val progressDialog = ProgressDialog(this);
        progressDialog.setTitle("Generate Offline Map Job")
        progressDialog.setMessage("Taking map offline...")
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        progressDialog.isIndeterminate = false
        progressDialog.progress = 0
        btnSave.setOnClickListener {

            progressDialog.show()

            // delete any offline map already in the cache
            val tempDirectoryPath = cacheDir.toString() + File.separator + "offlineMap"
            deleteDirectory(File(tempDirectoryPath))

            // specify the extent, min scale, and max scale as parameters
            var minScale = mapView.mapScale
            val maxScale = mapView.map.maxScale
            if (minScale <= maxScale) {
                minScale = maxScale + 1
            }

            val generateOfflineMapParameters = GenerateOfflineMapParameters(downloadArea.geometry, minScale, maxScale)

            // create an offline map offlineMapTask with the map
            val offlineMapTask = OfflineMapTask(mapView.map)
            // create an offline map job with the download directory path and parameters and start the job
            val job = offlineMapTask.generateOfflineMap(generateOfflineMapParameters, tempDirectoryPath)

            job.addJobDoneListener {
                if (job.status == Job.Status.SUCCEEDED) {
                    val result = job.result
                    mapView.map = result.offlineMap
                    mGraphicsOverlay.graphics.clear()
                    btnSave.setEnabled(false)
                    Toast.makeText(this, "Now displaying offline map.", Toast.LENGTH_LONG).show()
                } else {
                    val error = "Error in generate offline map job: " + job.error.additionalMessage
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    Log.e("TAG", error)
                }
                progressDialog.dismiss()
            }
            // show the job's progress with the progress dialog
            job.addProgressChangedListener { progressDialog.progress = job.progress }

            // start the job
            job.start();
        }
    }

    private fun deleteDirectory(file: File) {
        if (file.isDirectory)
            for (subFile in file.listFiles()) {
                deleteDirectory(subFile)
            }
        if (!file.delete()) {
            Log.e("TAG", "Failed to delete file: " + file.path)
        }
    }

    /*private fun setupOfflineMapTaskAndGenerateOfflineMapParameters() {
        btnSave.setOnClickListener {

            var minScale = mapView.mapScale
            val maxScale = mapView.map.maxScale
            Toast.makeText(this, "save", Toast.LENGTH_LONG).show()
            if (minScale <= maxScale) {
                minScale = maxScale + 1;
            }

            offlineMapTask = OfflineMapTask(mapView.map)
            val generateOfflineMapParametersFuture = offlineMapTask
                .createDefaultGenerateOfflineMapParametersAsync(downloadArea.geometry, minScale, maxScale)

            generateOfflineMapParametersFuture.addDoneListener {
                try {
                    generateOfflineMapParameters = generateOfflineMapParametersFuture.get()

                    samplesDirectory =
                        File(Environment.getExternalStorageDirectory().absolutePath + getString(R.string.samples_directory))


                    val localBasemapFileName = generateOfflineMapParameters.referenceBasemapFilename
                    if (!localBasemapFileName.isEmpty()) {
                        val localBasemapFile = File(samplesDirectory, localBasemapFileName)

                        localBasemapFile.createNewFile()
                        *//*if (localBasemapFile != null) {
                            mLocalBasemapDirectory = localBasemapFile.parent
                            Log.d("TAG", "basemap file found in: $mLocalBasemapDirectory")
                        } else {
                            val error = "Local basemap file $localBasemapFileName not found!"
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                            Log.e("TAG", error)
                        }*//*
                    } else {
                        val message = "The map's author has not specified a local basemap"
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        Log.i("TAG", message)
                    }
                } catch (e: ExecutionException) {
                    val error = "Error creating generate offline map parameters: " + e.message;
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    Log.e("exception", error);
                }
            }
        }
        btnShow.setOnClickListener {

        }

    }*/

    /*private fun setUpMethod() {

        val basemapType = Basemap.Type.TOPOGRAPHIC
        val latitude = 34.09042
        val longitude = -118.71511
        val levelOfDetails = 10
        val map = ArcGISMap(basemapType, latitude, longitude, levelOfDetails)
        mapView.map = map

        addLayer(map)
    }*/

    /*private fun addLayer(map: ArcGISMap) {

        val itemId = "41281c51f9de45edaf1c8ed44bb10e30"
        val portal = Portal("http://www.arcgis.com")
        val portalItemId = PortalItem(portal, itemId)
        val featureLayer = FeatureLayer(portalItemId, 0)
        featureLayer.addDoneLoadingListener {
            if (featureLayer.loadStatus === LoadStatus.LOADED) {
                map.operationalLayers.add(featureLayer)
            }
        }
        featureLayer.loadAsync()
    }*/

    override fun onPause() {
        mapView.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.resume()
    }

    override fun onDestroy() {
        mapView.dispose()
        super.onDestroy()
    }
}
