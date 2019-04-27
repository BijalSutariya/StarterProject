package com.example.starterproject

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button
import android.widget.Toast
import com.esri.arcgisruntime.layers.FeatureLayer
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.view.MapView
import com.esri.arcgisruntime.portal.Portal
import com.esri.arcgisruntime.portal.PortalItem
import com.esri.arcgisruntime.loadable.LoadStatus

class MainActivity : AppCompatActivity(), View.OnClickListener {
    override fun onClick(view: View) {
        when(view.id){
            R.id.btnSave -> {
                Toast.makeText(this,"save",Toast.LENGTH_LONG).show()
            }
            R.id.btnShow -> {
                Toast.makeText(this,"show",Toast.LENGTH_LONG).show()

            }
        }
    }

    private lateinit var mapView: MapView
    private lateinit var btnSave: Button
    private lateinit var btnShow: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnSave = this.findViewById(R.id.btnSave)
        btnShow = this.findViewById(R.id.btnShow)
        mapView = this.findViewById(R.id.arcgisMapview)

        btnSave.setOnClickListener(this)
        btnShow.setOnClickListener(this)
        setUpMethod()

    }



    private fun setUpMethod() {

        val basemapType = Basemap.Type.TOPOGRAPHIC
        val latitude = 34.09042
        val longitude = -118.71511
        val levelOfDetails = 10
        val map = ArcGISMap(basemapType, latitude, longitude, levelOfDetails)
        mapView.map = map

        addLayer(map)
    }

    private fun addLayer(map: ArcGISMap) {

        val itemId = "41281c51f9de45edaf1c8ed44bb10e30"
        val portal = Portal("http://www.arcgis.com")
        val portalItemId = PortalItem(portal,itemId)
        val featureLayer = FeatureLayer(portalItemId,0)
        featureLayer.addDoneLoadingListener {
            if (featureLayer.loadStatus === LoadStatus.LOADED) {
                map.operationalLayers.add(featureLayer)
            }
        }
        featureLayer.loadAsync()
    }

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
