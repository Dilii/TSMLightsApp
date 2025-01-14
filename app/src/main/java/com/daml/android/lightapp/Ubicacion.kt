package com.daml.android.lightapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.tasks.OnSuccessListener
import android.content.pm.PackageManager
import android.os.Build
import android.Manifest
import android.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationRequest
import android.content.Intent
import android.widget.Toast
import android.content.DialogInterface
import android.content.IntentSender.SendIntentException
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.Nullable
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.tasks.Task
import java.nio.charset.Charset
import kotlin.math.*

lateinit var mFusedLocationClient: FusedLocationProviderClient
lateinit var  fusedLocationClient: FusedLocationProviderClient
lateinit var mLocationRequest: LocationRequest
lateinit var mlocationCallback: LocationCallback
lateinit var builder: LocationSettingsRequest.Builder
var REQUEST_CHECK_SETTINGS = 102

//ref https://stackoverflow.com/questions/41500765/how-can-i-get-continuous-location-updates-in-android-like-in-google-maps

class ubi2 : AppCompatActivity() {
    var permitedDistance = 200.0

    // Enviar userLatitude y userLongitude a SOS
    var userLatitude = 0.0
    var userLongitude = 0.0

    var homeLatitude = 0.0
    var homeLongitude = 0.0
    var isHome = false
    var outOfHome = false //Cambia a true cuando esta fuera del rango

    var haveHome = false //true si ya se coloco localizacion de la casa

    var focosApagados= false //Variable auxiliar en el control de las peticiones mandadas a la base

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ubicacion)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fetchLastLocation()
        mlocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (locationResult == null) {
                    return
                }
                for (location in locationResult.locations) {
                    // Update UI with location data
                    // ...
                    //Log.e("CONTINIOUSLOC: ", location.toString())
                    userLatitude = location.latitude.toDouble()
                    userLongitude = location.longitude.toDouble()
                    if(isHome){
                        haveHome = true
                        homeLatitude = location.latitude.toDouble()
                        homeLongitude = location.longitude.toDouble()
                        outOfHome = false
                        isHome = false
                        println("Setting home")
                    }
                    println("User loc")
                    println(userLatitude)
                    println(userLongitude)
                    if(haveHome) {
                        var res = isInLocation(userLatitude, userLongitude)
                        outOfHome = res
                        print("Prendiendo focos :" )
                        println(outOfHome)

                        if (outOfHome) {
                            prenderFocos()
                            val label_on : TextView = findViewById(R.id.onLabel)
                            label_on.text = "¡Focos prendidos!"
                        }
                        if (!outOfHome) focosApagados = true

                        if (focosApagados){
                            val label_on : TextView = findViewById(R.id.onLabel)
                            label_on.text = ""
                        }
                        //Función que apaga y prende los focos en la base de datos
                        //Con la variable focosEncendidos se evita que se manden demasiadas peticiones a la base

                        if(outOfHome && focosApagados) {
                            for(num in 1..6){
                                changeStatusOnOff(num,outOfHome)
                            }

                            focosApagados = false
                        }
                    }
                }
            }
        }

        mLocationRequest = createLocationRequest()
        builder = LocationSettingsRequest.Builder()
            .addLocationRequest(mLocationRequest)
        checkLocationSetting(builder)

    }

    // FUNCTIONS FOR APPLICATION

    // Funcion que se activa al salir del rango permitido
    fun prenderFocos(){
        println("¡Prendiendo focos!")
    }


    fun setHome(view: View){
        isHome =true
        val label_homeSetted : TextView = findViewById(R.id.lblHomeSetted)
        label_homeSetted.text = "¡Hogar establecido!"

    }

    fun isInLocation(latitude:Double,longitude:Double): Boolean{
        //Distancia del punto actual al punto donde se ubico la casa
        //var distanceFromLocation = sqrt((userLatitude-latitude).pow(2)+(userLongitude-longitude).pow(2))
        var distanceFromLocation  = distanciaM(homeLatitude,homeLongitude,latitude,longitude)
        //println(permitedDistance)
        println("Diferencia:")
        println(distanceFromLocation)
        return (!(distanceFromLocation <= permitedDistance))
    }

    //Función onClick del botón OK

    fun setPermitedDistance(view: View){
        var editTextDistance = findViewById(R.id.km_input) as EditText
        val label_distance : TextView = findViewById(R.id.detectKM)

        permitedDistance = editTextDistance.text.toString().toDouble()
        label_distance.text = "Distancia establecida: "+permitedDistance.toString()+" metros"
        println(permitedDistance)
    }

    fun distanciaM(lat1:Double,long1:Double,lat2:Double,long2:Double): Double
    {
        val R:Double = 6378000.0 //Radio de la Tierra en m
        var difLatitud = (lat2-lat1)*3.141592/180
        var difLongitud = (long2-long1)*3.141592/180
        //a = a = sin²(Δlat/2) + cos(lat1) · cos(lat2) · sin²(Δlong/2)
        var a = (sin(difLatitud/2).pow(2))+ cos(lat1) * cos(lat2) *(sin(difLongitud/2).pow(2))
        //c = 2 · atan2(√a, √(1−a))
        var c = 2* atan2(sqrt(a), sqrt((1-a)))
        var d = R*c
        return d
    }

    //--------------------------------------------------------------------------
    // Functions for location
    private fun fetchLastLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
//                    Toast.makeText(MainActivity.this, "Permission not granted, Kindly allow permission", Toast.LENGTH_LONG).show();
                showPermissionAlert()
                return
            }
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener(
                this
            ) { location ->
                // Got last known location. In some rare situations this can be null.
                if (location != null) {
                    // Logic to handle location object
                    Log.e(
                        "LAST LOCATION: ",
                        location.toString()
                    ) // You will get your last location here
                }
            }
    }

//Methods for permission request
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
            123 -> {

                // If request is cancelled, the result arrays are empty.
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    // permission was denied, show alert to explain permission
                    showPermissionAlert()
                } else {
                    //permission is granted now start a background service
                    if (ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        && ActivityCompat.checkSelfPermission(
                            applicationContext,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        fetchLastLocation()
                    }
                }
            }
        }
    }

    private fun showPermissionAlert() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                123
            )
        }
    }

    protected fun createLocationRequest(): LocationRequest {
        val mLocationRequest = LocationRequest.create()
        mLocationRequest.interval = 1000
        mLocationRequest.fastestInterval = 500
        mLocationRequest.smallestDisplacement = 3f
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        return mLocationRequest
    }

    private fun checkLocationSetting(builder: LocationSettingsRequest.Builder) {
        val client = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener(
            this,
            OnSuccessListener<LocationSettingsResponse?> { // All location settings are satisfied. The client can initialize
                // location requests here.
                // ...
                startLocationUpdates()
                return@OnSuccessListener
            })
        task.addOnFailureListener(this, OnFailureListener { e ->
            if (e is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                val builder1: AlertDialog.Builder = AlertDialog.Builder(this)
                builder1.setTitle("Continious Location Request")
                builder1.setMessage("This request is essential to get location update continiously")
                builder1.create()
                builder1.setPositiveButton("OK",
                    DialogInterface.OnClickListener { dialog, which ->
                        try {
                            e.startResolutionForResult(
                                this,
                                REQUEST_CHECK_SETTINGS
                            )
                        } catch (e1: SendIntentException) {
                            e1.printStackTrace()
                        }
                    })
                builder1.setNegativeButton("Cancel",
                    DialogInterface.OnClickListener { dialog, which ->
                        Toast.makeText(
                            this,
                            "Location update permission not granted",
                            Toast.LENGTH_LONG
                        ).show()
                    })
                builder1.show()
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == RESULT_OK) {
                // All location settings are satisfied. The client can initialize
                // location requests here.
                startLocationUpdates()
            } else {
                checkLocationSetting(builder)
            }
        }
    }


    fun startLocationUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    Activity#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for Activity#requestPermissions for more details.
                return
            }
        }
        fusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mlocationCallback,
            null /* Looper */
        )
    }


    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(mlocationCallback)
    }

// -----------------------------------------------------------------------------------------------//
// CONEXIÓN CON LA BASE DE DATOS
// -----------------------------------------------------------------------------------------------//

    fun changeStatusOnOff(bulbNumber: Int, outOfHome: Boolean): Boolean{
        val queue = Volley.newRequestQueue(this)
        val url = "https://appdevops.000webhostapp.com/crud.php"
        val state = if (outOfHome) 1 else 0
        val requestBody = "id=${bulbNumber}" + "&editar=1" + "&intensidad=80" + "&estado=${state}"
        var success = false

        val stringRequest: StringRequest =
            object : StringRequest(Method.POST, url,
                Response.Listener { response ->
                    // response
                    var strResp = response.toString()
                    Log.d("API", strResp)
                    success = true
                },
                Response.ErrorListener { error ->
                    Log.e("API", "error => $error")
                    var correctToast = Toast.makeText(this, R.string.db_error, Toast.LENGTH_SHORT)
                    correctToast.setGravity(Gravity.TOP, 0, 0)
                    correctToast.show()
                }
            ) {
                override fun getBody(): ByteArray {
                    return requestBody.toByteArray(Charset.defaultCharset())
                }
            }

// Add the request to the RequestQueue.
        queue.add(stringRequest)
        return success
    }
}




/*
var bulbOneIsLit = false
var bulbTwoIsLit = false
var bulbThreeIsLit = false
var bulbFourIsLit = false

fun changeStatus(bulbNumber: Int, isLit: Boolean): Boolean{
    val queue = Volley.newRequestQueue(this)
    val url = "https://appdevops.000webhostapp.com/crud.php"
    val state = if (isLit) 0 else 1
    val image = if (isLit) R.drawable.offbulb else R.drawable.onbulb
    val requestBody = "id=${bulbNumber}" + "&editar=1" + "&intensidad=80" + "&estado=${state}"
    var success = false

    val stringRequest : StringRequest =
        object : StringRequest(
            Method.POST, url,
            Response.Listener { response ->
                // response
                var strResp = response.toString()
                Log.d("API", strResp)
                success = true
            },
            Response.ErrorListener { error ->
                Log.e("API", "error => $error")
                var correctToast = Toast.makeText(this, R.string.db_error, Toast.LENGTH_SHORT)
                correctToast.setGravity(Gravity.TOP,0,0)
                correctToast.show()
            }
        ){
            override fun getBody(): ByteArray {
                return requestBody.toByteArray(Charset.defaultCharset())
            }
        }

// Add the request to the RequestQueue.
    queue.add(stringRequest)
    return success
} */