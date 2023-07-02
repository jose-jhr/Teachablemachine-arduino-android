package com.ingenieriajhr.testtf

import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.ingenieriajhr.blujhr.BluJhr
import com.ingenieriajhr.testtf.databinding.ActivityMainBinding
import com.ingenieriiajhr.jhrCameraX.BitmapResponse
import com.ingenieriiajhr.jhrCameraX.CameraJhr

class MainActivity : AppCompatActivity() {


    lateinit var binding : ActivityMainBinding
    lateinit var cameraJhr: CameraJhr

    lateinit var classifyTf: ClassifyTf

    companion object{
        const val INPUT_SIZE = 224
    }

    lateinit var blue:BluJhr

    var conectExit = false

    var conectionBtSuccesfull = false

    var arrayDevice = ArrayList<String>()

    val classes = arrayOf("IRON","PELOTICA","NORMAL")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        classifyTf = ClassifyTf(this)

        //init cameraJHR
        cameraJhr = CameraJhr(this)

        //btn Conectar
        binding.btnConectar.setOnClickListener {
            blue.onBluetooth()
            if (conectExit){
                conectarBt()
            }
        }

        //init blue
        blue = BluJhr(this)

    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (cameraJhr.allpermissionsGranted() && !cameraJhr.ifStartCamera){
            startCameraJhr()
        }else{
            cameraJhr.noPermissions()
        }
    }

    /**
     * start Camera Jhr
     */
    private fun startCameraJhr() {
        var timeRepeat = System.currentTimeMillis()
        cameraJhr.addlistenerBitmap(object : BitmapResponse {
            override fun bitmapReturn(bitmap: Bitmap?) {
                if (bitmap!=null){
                    if (System.currentTimeMillis()>timeRepeat+1000){
                        classifyImage(bitmap)
                        timeRepeat = System.currentTimeMillis()
                    }
                }
            }
        })

        cameraJhr.initBitmap()
        cameraJhr.initImageProxy()
        //selector camera LENS_FACING_FRONT = 0;    LENS_FACING_BACK = 1;
        //aspect Ratio  RATIO_4_3 = 0; RATIO_16_9 = 1;  false returImageProxy, true return bitmap
        cameraJhr.start(0,0,binding.cameraPreview,true,false,true)

    }

    private fun classifyImage(bitmap: Bitmap) {
        //224*224
        val bitmapScale = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE,false)

        classifyTf.listenerInterpreter(object :ReturnInterpreter{
            override fun classify(confidence: FloatArray, maxConfidence: Int) {
                runOnUiThread {
                    binding.txtResult.text = "${classes[0]}: ${confidence[0].decimal()}\n" +
                            "\"${classes[1]}: ${confidence[1].decimal()}\n" +
                            "\"${classes[2]}: ${confidence[2].decimal()}\n" +
                            "mejor: ${classes[maxConfidence]}"
                    if (conectionBtSuccesfull){
                        blue.bluTx(classes[maxConfidence].substring(0,1))
                    }
                }
            }

        })

        classifyTf.classify(bitmapScale)

        runOnUiThread {
            binding.imgBitMap.setImageBitmap(bitmapScale)
        }

    }


    private fun Float.decimal():String{
        return "%.2f".format(this)
    }

    private fun conectarBt(){
        if (conectExit){
            val dialog = AlertDialog.Builder(this)
            dialog.setTitle("Dispositivos vinculados")
            val inflater = layoutInflater.inflate(R.layout.listview,null)
            dialog.setView(inflater)
            val alert = dialog.create()
            alert.show()

            val listViewDevice = inflater.findViewById<ListView>(R.id.listDevice)
            arrayDevice = blue.deviceBluetooth()
            if (arrayDevice.isEmpty()){
                arrayDevice.add("No hay dispositivos")
            }else{
                val adapter = ArrayAdapter(this,android.R.layout.simple_list_item_1,arrayDevice)
                listViewDevice.adapter = adapter

                listViewDevice.setOnItemClickListener { adapterView, view, i, l ->
                    val deviceConect = arrayDevice[i]
                    blue.connect(deviceConect)

                    /**add method listener update state conection**/
                    blue.setDataLoadFinishedListener(object :BluJhr.ConnectedBluetooth{
                        override fun onConnectState(state: BluJhr.Connected) {
                            when(state){

                                BluJhr.Connected.True->{
                                    toastMsg("Conectado a: "+deviceConect.substring(0,deviceConect.length-17))
                                    conectionBtSuccesfull = true
                                    alert.dismiss()
                                }

                                BluJhr.Connected.Disconnect->{
                                    toastMsg("Bluetooth Desconectado")
                                    conectionBtSuccesfull = false
                                    alert.dismiss()
                                }

                                BluJhr.Connected.Pending->{
                                    toastMsg("Conectando")
                                    conectionBtSuccesfull = false
                                    alert.dismiss()
                                }

                                BluJhr.Connected.False->{
                                    toastMsg("No se pudo conectar dispositivo")
                                    conectionBtSuccesfull = false
                                }

                                else -> {

                                }

                            }
                        }

                    })

                }
            }

        }else{
            Toast.makeText(this,"No tienes los permisos necesarios",Toast.LENGTH_SHORT).show()
        }
    }


    private fun toastMsg(msg: String){
        Toast.makeText(applicationContext, msg,Toast.LENGTH_SHORT).show()
    }




    //permisos bluetooth
    /**
     * pedimos los permisos correspondientes, para android 12 hay que pedir los siguientes BLUETOOTH_SCAN y BLUETOOTH_CONNECT
     * en android 12 o superior se requieren permisos adicionales
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (blue.checkPermissions(requestCode,grantResults)){
            Toast.makeText(this, "Exit", Toast.LENGTH_SHORT).show()
            blue.initializeBluetooth()
        }else{
            if(Build.VERSION.SDK_INT < Build.VERSION_CODES.S){
                blue.initializeBluetooth()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /**
     * On Bluetooth
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!blue.stateBluetoooth() && requestCode == 100){
            blue.initializeBluetooth()
        }else{
            if (requestCode == 100){
                    conectExit = true
                    conectarBt()
                }else{
                    Toast.makeText(this, "No tienes vinculados dispositivos", Toast.LENGTH_SHORT).show()
                }
            }
        super.onActivityResult(requestCode, resultCode, data)
    }

}