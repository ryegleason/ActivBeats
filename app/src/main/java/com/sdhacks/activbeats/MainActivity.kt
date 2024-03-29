package com.sdhacks.activbeats

import a5.com.a5bluetoothlibrary.A5BluetoothCallback
import a5.com.a5bluetoothlibrary.A5Device
import a5.com.a5bluetoothlibrary.A5DeviceManager
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.ContextCompat.getSystemService
import android.support.v4.content.FileProvider
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.transition.Slide
import android.transition.TransitionManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.sdhacks.activbeats.wav.WavFile
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.ArrayList


private const val TAG = "ACTIVBEATS"
private const val BUFFER = 100
private const val TRACK_LEN = 10.0
private const val TRACK_LEN_MILLIS : Long = (TRACK_LEN * 1000).toLong()
private const val MAX_STRENGTH = 100
private const val APPROX_PERIOD = 100
private const val MAGIC = 8

private const val CURSOR_START = 300.0.toFloat()
private const val CURSOR_END = 2035.0.toFloat()

private const val TIME_OFFSET_MILLIS = 50

class MainActivity : AppCompatActivity(), A5BluetoothCallback {

    enum class Instrument(val index: Int) {
        Snare(0), Kick(1), HighHat(2), TomTom(3)
    }

    private var connectedDevices = mutableListOf<A5Device?>()
    private var device: A5Device? = null
    private var counter: Int = 0
    private var countDownTimer: CountDownTimer? = null
    private var timeIsoStarted: Long = 0
    private var factories = arrayListOf<Sample.SampleFactory>()
    private var players = arrayListOf<MediaPlayer>()
    private var currentlyHit = false
    private var hitStart: Long = 0
    private var hitMax = 0
    private var samples = arrayOf(arrayListOf(), arrayListOf(), arrayListOf(), arrayListOf<Sample>())
    private var instrument = Instrument.HighHat
    private var otrPlayer: MediaPlayer? = null
    private var switchedScreen = false
    private var lastExport : Uri? = null
    private var marginPos = intArrayOf(CURSOR_END.toInt(), CURSOR_END.toInt(), CURSOR_END.toInt(), CURSOR_END.toInt())
    private var lines = arrayListOf(ArrayList<ImageView>(), ArrayList(), ArrayList(), ArrayList())


    private lateinit var deviceAdapter: DeviceAdapter

    override fun bluetoothIsSwitchedOff() {
        Toast.makeText(this, "bluetooth is switched off", Toast.LENGTH_SHORT).show()
    }

    override fun searchCompleted() {
        Toast.makeText(this, "search completed", Toast.LENGTH_SHORT).show()
    }

    override fun didReceiveIsometric(device: A5Device, value: Int) {
        manageReceiveIsometric(device, value)
    }

    override fun onWriteCompleted(device: A5Device, value: String) {
    }

    override fun deviceConnected(device: A5Device) {
    }

    override fun deviceFound(device: A5Device) {
        deviceAdapter.addDevice(device)
        connectedDevices.add(device)
    }

    override fun deviceDisconnected(device: A5Device) {
    }

    override fun on133Error() {
    }

    object Values {
        const val REQUEST_ENABLE_INTENT = 999
        const val MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 998
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cursorWrapper.x = CURSOR_START
        cursorWrapper.y = 0.0f

        otrPlayer = MediaPlayer.create(this, R.raw.otr)

        factories = arrayListOf(Sample.SampleFactory(WavFile.openWavFile(resources.openRawResource(R.raw.snare)), Instrument.Snare),
        Sample.SampleFactory(WavFile.openWavFile(resources.openRawResource(R.raw.kick)), Instrument.Kick),
        Sample.SampleFactory(WavFile.openWavFile(resources.openRawResource(R.raw.highhat)), Instrument.HighHat),
        Sample.SampleFactory(WavFile.openWavFile(resources.openRawResource(R.raw.tomtom)), Instrument.TomTom))

        players = arrayListOf(MediaPlayer.create(this, R.raw.snare),
            MediaPlayer.create(this, R.raw.kick),
            MediaPlayer.create(this, R.raw.highhat),
            MediaPlayer.create(this, R.raw.tomtom))

        requestPermission()
        initRecyclerView()

        connectButton.setOnClickListener {
            val device = this.device
            if (device != null) {
                A5DeviceManager.connect(this, device)
            }
        }

        scanDevices.setOnClickListener {
            for (device in connectedDevices) {
                device?.disconnect()
            }
            device?.disconnect()
            device = null
            connectedDevices.clear()
            deviceAdapter.clearDevices()

            A5DeviceManager.scanForDevices()
        }

        exportButton.setOnClickListener {
            export("activbeat.wav")
        }

        shareButton.setOnClickListener {
            shareLastExport()
        }

        disconnectButton.setOnClickListener {
            device?.disconnect()
        }

        goButton.setOnClickListener {
            recyclerView.visibility = View.INVISIBLE
            connectButton.visibility = View.INVISIBLE
            scanDevices.visibility = View.INVISIBLE
            disconnectButton.visibility = View.INVISIBLE
            goButton.visibility = View.INVISIBLE
            tracksContainer.visibility = View.VISIBLE
            cursorWrapper.visibility = View.VISIBLE
            testImage.visibility = View.VISIBLE
            otrTrack.visibility = View.VISIBLE
            emptyTrack1.visibility = View.VISIBLE
            emptyTrack2.visibility = View.VISIBLE
            emptyTrack3.visibility = View.VISIBLE
            emptyTrack4.visibility = View.VISIBLE
            startCursor.visibility = View.VISIBLE
            exportButton.visibility = View.VISIBLE
            //currentbeat.visibility = View.VISIBLE
            beattype.visibility = View.VISIBLE
            shareButton.visibility = View.VISIBLE
            backButton.visibility = View.VISIBLE
            for (lineArr in lines){
                for (line in lineArr){
                    line.visibility = View.VISIBLE
                }
            }

            cursorWrapper.bringToFront()
            testImage.bringToFront()
            switchedScreen = true
            cursorWrapper.x = CURSOR_START
            cursorWrapper.y = 0.0f
        }

        backButton.setOnClickListener {
            recyclerView.visibility = View.VISIBLE
            connectButton.visibility = View.VISIBLE
            scanDevices.visibility = View.VISIBLE
            disconnectButton.visibility = View.VISIBLE
            goButton.visibility = View.VISIBLE

            tracksContainer.visibility = View.INVISIBLE
            cursorWrapper.visibility = View.INVISIBLE
            testImage.visibility = View.INVISIBLE
            otrTrack.visibility = View.INVISIBLE
            emptyTrack1.visibility = View.INVISIBLE
            emptyTrack2.visibility = View.INVISIBLE
            emptyTrack3.visibility = View.INVISIBLE
            emptyTrack4.visibility = View.INVISIBLE
            startCursor.visibility = View.INVISIBLE
            exportButton.visibility = View.INVISIBLE
            //currentbeat.visibility = View.INVISIBLE
            beattype.visibility = View.INVISIBLE
            shareButton.visibility = View.INVISIBLE
            backButton.visibility = View.INVISIBLE
            for (lineArr in lines){
                for (line in lineArr){
                    line.visibility = View.INVISIBLE
                }
            }

            switchedScreen = false
        }

        startCursor.setOnClickListener {
            onRecordPressed()
        }

        emptyTrack1.setOnClickListener {
            if (System.currentTimeMillis() > timeIsoStarted + TRACK_LEN_MILLIS) {
                players[Instrument.HighHat.index].seekTo(0)
                players[Instrument.HighHat.index].start()
                show_popup(R.layout.popup_1)
                beattype.text = resources.getString(R.string.highhat)
                instrument = Instrument.HighHat
            }
        }
        emptyTrack2.setOnClickListener {
            if (System.currentTimeMillis() > timeIsoStarted + TRACK_LEN_MILLIS) {
                players[Instrument.Snare.index].seekTo(0)
                players[Instrument.Snare.index].start()
                show_popup(R.layout.popup_2)
                beattype.text = resources.getString(R.string.snare)
                instrument = Instrument.Snare
            }
        }
        emptyTrack3.setOnClickListener {
            if (System.currentTimeMillis() > timeIsoStarted + TRACK_LEN_MILLIS) {
                players[Instrument.Kick.index].seekTo(0)
                players[Instrument.Kick.index].start()
                show_popup(R.layout.popup_3)
                beattype.text = resources.getString(R.string.kick)
                instrument = Instrument.Kick
            }
        }
        emptyTrack4.setOnClickListener {
            if (System.currentTimeMillis() > timeIsoStarted + TRACK_LEN_MILLIS) {
                players[Instrument.TomTom.index].seekTo(0)
                players[Instrument.TomTom.index].start()
                show_popup(R.layout.popup_4)
                beattype.text = resources.getString(R.string.tomtom)
                instrument = Instrument.TomTom
            }
        }
    }

    private fun onRecordPressed() {
        currentlyHit = false
        otrPlayer?.seekTo(0)
        otrPlayer?.start()
        samples[instrument.index] = ArrayList()
        marginPos[instrument.index] = CURSOR_END.toInt()
        runOnUiThread {
            val layout = when(instrument){
                Instrument.Snare -> snareLayout
                Instrument.Kick -> kickLayout
                Instrument.HighHat -> hihatLayout
                Instrument.TomTom -> tomtomLayout
            }

            for (line in lines[instrument.index]){
                layout.removeView(line)
            }
            lines[instrument.index].clear()
        }
        timeIsoStarted = System.currentTimeMillis()
        device?.startIsometric()
    }

    private fun onActivPress(time: Long, value: Int){
        runOnUiThread {
            val marker = ImageView(this)
            marker.setImageResource(R.drawable.mark)
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            layoutParams.setMargins((cursorPosition(time - TIME_OFFSET_MILLIS) - marginPos[instrument.index]).toInt() - MAGIC, 0, 0, 0)
//            layoutParams.setMargins(-MAGIC, 0, 0, 0)
            marginPos[instrument.index] = cursorPosition(time - TIME_OFFSET_MILLIS).toInt()
            marker.visibility = View.VISIBLE

            val layout = when(instrument){
                Instrument.Snare -> snareLayout
                Instrument.Kick -> kickLayout
                Instrument.HighHat -> hihatLayout
                Instrument.TomTom -> tomtomLayout
            }

            layout.addView(marker, layoutParams)
            layout.invalidate()
            lines[instrument.index].add(marker)
        }
//        players[instrument.index].seekTo(0)
//        players[instrument.index].start()
        currentlyHit = true
        hitMax = value
        hitStart = time - TIME_OFFSET_MILLIS
    }

    private fun onActivRelease(time: Long, value: Int){
        currentlyHit = false
        samples[instrument.index].add(factories[instrument.index].getSample(hitMax /*- MAX_STRENGTH/4*/, time - hitStart, hitStart - timeIsoStarted))
    }

    private fun cursorPosition(time: Long) : Float {
        return CURSOR_START + (CURSOR_END - CURSOR_START) * (time - timeIsoStarted).toFloat() / TRACK_LEN_MILLIS.toFloat()
    }

    private fun manageReceiveIsometric(thisDevice: A5Device, thisValue: Int) {
        val time = System.currentTimeMillis()
//        print(thisDevice.device.name, thisValue)
        if (switchedScreen) {
            if (time > timeIsoStarted + TRACK_LEN_MILLIS) {
                thisDevice.stop()
                cursorWrapper.x = CURSOR_END
                if (currentlyHit) {
                    onActivRelease(timeIsoStarted + TRACK_LEN_MILLIS, 0)
                }
            } else {
                if (currentlyHit) {
                    if (thisValue < MAX_STRENGTH / 4) {
                        onActivRelease(time, thisValue)
                    } else {
                        hitMax = kotlin.math.max(hitMax, thisValue)
                    }
                } else if (thisValue >= MAX_STRENGTH / 4) {
                    onActivPress(time, thisValue)
                }

                for (otherInstrument in arrayOf(Instrument.Snare, Instrument.Kick, Instrument.HighHat, Instrument.TomTom)) {
                    if (otherInstrument != instrument) {
                        for (sample in samples[otherInstrument.index]) {
                            if (sample.start * 1000 > (time - timeIsoStarted) && sample.start * 1000 < (time - timeIsoStarted) + APPROX_PERIOD) {
                                players[sample.instrument.index].seekTo(0)
                                players[sample.instrument.index].start()
                            }
                        }
                    }
                }
                cursorWrapper.x = cursorPosition(time)
                cursorWrapper.y = 0.0f
            }
        }
    }

    private fun export(filename: String) {
        Log.v(TAG, "export started")

        var max = 1
        for (sampleByInst in samples){
            for (sample in sampleByInst) {
                max = kotlin.math.max(max, sample.peak)
                Log.v(TAG, "$sample")
            }
        }

        val otrData = WavFile.getRaw(WavFile.openWavFile(resources.openRawResource(R.raw.otr)), 0)
        val outFile = File(getExternalFilesDir(null),filename)
        val wavFile = WavFile.newWavFile(FileOutputStream(outFile), 1,
            (otrData.size - 1).toLong(), factories[0].validBits, factories[0].sampleRate)

        val buffer = DoubleArray(BUFFER)

        Log.v(TAG, "Output file opened")

        var frameCounter: Long = 0

        // Loop until all frames written
        while (frameCounter < wavFile.numFrames) {
            // Determine how many frames to write, up to a maximum of the buffer size
            val remaining = wavFile.framesRemaining
//            Log.v(TAG, "$remaining frames left")
            val toWrite = if (remaining > BUFFER) BUFFER else remaining.toInt()

            // Fill the buffer
            for (i in 0 until toWrite){
                var dat = 0.0

                //Add in each sample
                for (sampleByInst in samples){
                    for (sample in sampleByInst) {
                        dat += sample.getClipAtFrame(frameCounter)/max
                    }
                }
                dat += otrData[frameCounter.toInt()]

                buffer[i] = dat

                frameCounter++
            }

            // Write the buffer
            wavFile.writeFrames(buffer, toWrite)
        }

        Log.v(TAG, "Export finished")

        wavFile.close()
        lastExport = FileProvider.getUriForFile(this, this.applicationContext.packageName + ".provider", outFile)

        val outputPlayer = MediaPlayer.create(this, lastExport)
        outputPlayer.start()
    }

    private fun shareLastExport() {
        if (lastExport == null) {
            Toast.makeText(this, "export first, then share!", Toast.LENGTH_SHORT).show()
        } else {
            val share = Intent(Intent.ACTION_SEND)
            share.type = "audio/wav"
            share.putExtra(Intent.EXTRA_STREAM, lastExport)
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, "Share Workout Mix"))
        }
    }

    private fun stretchTimeSeries(data : ArrayList<Int>, time : ArrayList<Long>, newLength : Int) : FloatArray {
        val toRet = FloatArray(newLength)
        val start = time[0]
        val timeStep : Double = (time[time.size - 1] - start).toDouble() / (newLength - 1).toDouble()
        var currentIndex = 0
        for (i in toRet.indices) {
            val thisTime = i*timeStep
            while(thisTime > (time[currentIndex + 1] - start)){
                currentIndex++
                if (currentIndex >= time.size - 1){
                    toRet.fill(data[data.size - 1].toFloat(), i)
                    return toRet
                }
            }
            val linearInterp : Double = (thisTime - (time[currentIndex] - start)) / (time[currentIndex + 1] - time[currentIndex]).toDouble()
            toRet[i] = data[currentIndex].toFloat() + linearInterp.toFloat()*(data[currentIndex + 1] - data[currentIndex]).toFloat()
        }
        return toRet
    }

    fun deviceSelected(device: A5Device) {
        this.device = device
        Toast.makeText(this, "device selected: " + device.device.name, Toast.LENGTH_SHORT).show()
    }

    private fun initRecyclerView() {
        deviceAdapter = DeviceAdapter(this)

        val linearLayoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = linearLayoutManager
        recyclerView.adapter = deviceAdapter
    }

    private fun requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is not granted
                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                ) {
                    // Show an explanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                } else {
                    // No explanation needed, we can request the permission.
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                        Values.MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION
                    )

                    // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                    // app-defined int constant. The callback method gets the
                    // result of the request.
                }
            } else {
                startBluetooth()
            }
        } else {
            startBluetooth()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            Values.MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    startBluetooth()
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == Values.REQUEST_ENABLE_INTENT) {
            if (resultCode == Activity.RESULT_OK) {
                startBluetooth()
            }
        }
    }

    private fun startBluetooth() {
        val bluetoothManager = A5App().getInstance().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, Values.REQUEST_ENABLE_INTENT)
        } else {
            A5DeviceManager.setCallback(this)
            A5DeviceManager.scanForDevices()
        }
    }

    private fun startTimer() {
        counter = 0
        countDownTimer = object : CountDownTimer(420000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                counter++
            }

            override fun onFinish() {
            }
        }.start()
    }

    private fun stopTimer() {
        countDownTimer?.cancel()
    }

    private fun show_popup(popup: Int){

        // Initialize a new layout inflater instance
        val inflater:LayoutInflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        // Inflate a custom view using layout inflater
        val view = inflater.inflate(popup,null)

        // Initialize a new instance of popup window
        val popupWindow = PopupWindow(
            view, // Custom view to show in popup window
            1500, // Width of popup window
            750 // Window height
        )

        // Set an elevation for the popup window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            popupWindow.elevation = 10.0F
        }


        // If API level 23 or higher then execute the code
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            // Create a new slide animation for popup window enter transition
            val slideIn = Slide()
            slideIn.slideEdge = Gravity.TOP
            popupWindow.enterTransition = slideIn

            // Slide animation for popup window exit transition
            val slideOut = Slide()
            slideOut.slideEdge = Gravity.TOP
            popupWindow.exitTransition = slideOut

        }

        // Get the widgets reference from custom view
        val tv = view.findViewById<TextView>(R.id.text_view)
        val buttonPopup = view.findViewById<Button>(R.id.button_popup)

        // Set click listener for popup window's text view
        //tv.setOnClickListener{
            // Change the text color of popup window's text view
            //tv.setTextColor(Color.RED)
        //}

        // Set a click listener for popup's button widget
        buttonPopup.setOnClickListener{
            // Dismiss the popup window
            popupWindow.dismiss()
        }

        // Set a dismiss listener for popup window
        popupWindow.setOnDismissListener {
            Toast.makeText(applicationContext,"Popup closed",Toast.LENGTH_SHORT).show()
        }


        // Finally, show the popup window on app
        TransitionManager.beginDelayedTransition(root_layout)
        popupWindow.showAtLocation(
            root_layout, // Location to display popup window
            Gravity.CENTER, // Exact position of layout to display popup
            0, // X offset
            0 // Y offset
        )
    }
}
