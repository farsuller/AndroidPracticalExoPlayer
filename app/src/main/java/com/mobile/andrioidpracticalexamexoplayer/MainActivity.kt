package com.mobile.andrioidpracticalexamexoplayer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.util.Log
import com.google.android.exoplayer2.util.Util
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.mobile.andrioidpracticalexamexoplayer.databinding.ActivityMainBinding
import java.util.*
import kotlin.math.sqrt


class MainActivity : AppCompatActivity() {

    lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    lateinit var locationRequest: LocationRequest
    val PERMISSION_ID = 1010

    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val TAG = "MainActivity"
    private var player: SimpleExoPlayer? = null
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition = 0L

    lateinit var handler : Handler

    private val playbackStateListener: Player.EventListener = playbackStateListener()


 //   private val locationVm: LocationSharedViewModel by viewModels()

    private lateinit var sensorManager: SensorManager
    private lateinit var gyroSensor: Sensor
    private lateinit var audioManager: AudioManager
    private var acceleration = 0f
    private var currentAcceleration = 0f
    private var lastAcceleration = 0f

    var SPEED_NORMAL = 1f
    var SPEED_MEDIUM = 1.5f
    var SPEED_HIGH = 2f

    var stateString = ""

    var distance : Float = 0.0f
    var userLatLng = LatLng(0.0, 0.0)
    private fun initializePlayer() {
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
        player = SimpleExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer
                val mediaItem = MediaItem.fromUri(getString(R.string.media_url_mp4))
                exoPlayer.addMediaItem(mediaItem)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(currentWindow, playbackPosition)
                exoPlayer.addListener(playbackStateListener)
                exoPlayer.prepare()
            }
    }
    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        Objects.requireNonNull(sensorManager).registerListener(sensorListener, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
        acceleration = 10f
        currentAcceleration = SensorManager.GRAVITY_EARTH
        lastAcceleration = SensorManager.GRAVITY_EARTH
        // method to get the location

        audioManager = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create()
        getLastLocation()

        handler = Handler(Looper.getMainLooper())
    }
    private val sensorListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta: Float = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta
            if (acceleration > 12) {
                player?.playWhenReady = false
                player?.playbackState
            }

            val stringValue: String = x.toString() +"X" + " " + y.toString()+"Y" + " " + z.toString()+"Z"
            println("gyro sensor $stringValue")



            if(player?.playWhenReady == true){
                if(x > 3.5f) {
                    audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                } else if(x < -3.5f) {
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                }

                if(stateString !="ExoPlayer.STATE_BUFFERING")
                if(z > 5.0f) {
                     player?.currentPosition?.plus(1000)?.let { player?.seekTo(it) }
                } else if(z < -0.5f) {
                      player?.currentPosition?.plus(-1000)?.let { player?.seekTo(it) }
                }
            }


        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    fun getLastLocation(){
        if(CheckPermission()){
            if(isLocationEnabled()){
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                fusedLocationProviderClient.lastLocation.addOnCompleteListener { task->
                    val location:Location? = task.result
                    if(location != null){
                        handler.postDelayed(object : Runnable {
                            override fun run() {
                                saveOldLocation(location.latitude,location.longitude)
                                handler.removeCallbacks(this)
                            }
                        }, 5000)

                        newLocationData()
                    }
                }
            }else{
                Toast.makeText(this,"Please Turn on Your device Location",Toast.LENGTH_SHORT).show()
            }
        }else{
            RequestPermission()
        }
    }

    fun newLocationData(){
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = 2000
        locationRequest.fastestInterval = 1500
      //  locationRequest.numUpdates = 2
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        fusedLocationProviderClient.requestLocationUpdates(
            locationRequest,locationCallback, Looper.getMainLooper()
        )
    }


    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            var lastLocation: Location = locationResult.lastLocation
            Log.d("Debug:","your last last location: "+ lastLocation.longitude.toString())
            println("Your Last Location is : Long: ${lastLocation.longitude} , Lat: ${lastLocation.latitude}")


            //userLatLng = LatLng(lastLocation.latitude,lastLocation.longitude)

            getDistanceMeter(LatLng(lastLocation.latitude,lastLocation.longitude))
          //  textView.text = "You Last Location is : Long: "+ lastLocation.longitude + " , Lat: " + lastLocation.latitude + "\n" + getCityName(lastLocation.latitude,lastLocation.longitude)
        }
    }

    private fun CheckPermission():Boolean{
        //this function will return a boolean
        //true: if we have permission
        //false if not
        if(
            ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ){
            return true
        }

        return false

    }

    fun RequestPermission(){
        //this function will allows us to tell the user to requesut the necessary permsiion if they are not garented
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION),
            PERMISSION_ID
        )
    }

    fun isLocationEnabled():Boolean{
        //this function will return to us the state of the location service
        //if the gps or the network provider is enabled then it will return true otherwise it will return false
        var locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == PERMISSION_ID){
            if(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Log.d("Debug:","You have the Permission")
            }
        }
    }

    public override fun onResume() {
        super.onResume()
        hideSystemUi()
        if ((Util.SDK_INT < 24 || player == null)) {
            initializePlayer()
        }

        sensorManager.registerListener(sensorListener, sensorManager.getDefaultSensor(
            Sensor .TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    public override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
        if (Util.SDK_INT < 24) {
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= 24) {
            releasePlayer()
        }
    }

    private fun releasePlayer() {
        player?.run {
            playbackPosition = this.currentPosition
            currentWindow = this.currentWindowIndex
            playWhenReady = this.playWhenReady
            removeListener(playbackStateListener)
            release()
        }
        player = null
    }

    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        viewBinding.videoView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    private fun playbackStateListener() = object : Player.EventListener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            stateString = when (playbackState) {
                ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE"
                ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING"
                ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY"
                ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED"
                else -> "UNKNOWN_STATE"
            }
            Log.d(TAG, "changed state to $stateString")
        }
    }

    fun getDistanceMeter(newLatLng:LatLng){
        val sharedPreferences = this.getSharedPreferences("user_location", MODE_PRIVATE)

        val locationA = Location("Old Location")
        locationA.latitude = sharedPreferences.getString("user_latitude", "0.0")?.toDouble() ?: 0.0
        locationA.longitude = sharedPreferences.getString("user_longitude", "0.0")?.toDouble() ?: 0.0

        val locationB = Location("New Location")
        locationB.latitude = newLatLng.latitude
        locationB.longitude =  newLatLng.longitude

        distance = (locationA.distanceTo(locationB))

//        println("Your distance is old lat: ${locationA.latitude} is old long ${locationA.longitude}")
//        println("Your distance is new lat: ${locationB.latitude} is new long ${locationB.longitude}")
//        println("Your distance is  $distance m")

        if(distance > 10f){
            player?.seekTo(0)
        }
    }

    private fun saveOldLocation(latitude:Double,longitude:Double) {
        val sharedPreferences = this.getSharedPreferences("user_location", MODE_PRIVATE)
        val editor = sharedPreferences?.edit()
        editor?.putString("user_latitude", latitude.toString())
        editor?.putString("user_longitude", longitude.toString())
        editor?.apply()
    }
}