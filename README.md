# Teachablemachine-arduino-android
 ```kotlin
 //import de las dependencias
 
 // If you want to additionally use the CameraX View class
    implementation "androidx.camera:camera-view:1.3.0-alpha06"
    implementation 'com.github.jose-jhr:Library-CameraX:1.0.8'
 
    //BT
    implementation 'com.github.jose-jhr:blueJhrLibrary:0.1.0'
    
    //clases utilitarias que nos van a permitir en etapa de pre y post procesamiento
    implementation 'org.tensorflow:tensorflow-lite-support:+'
    implementation 'org.tensorflow:tensorflow-lite:+'
    implementation 'org.tensorflow:tensorflow-lite-metadata:0.1.0'
  ```
  
  en build gradle poner
  
   ```kotlin
    //no comprima el archivo que contiene el modelo
    aaptOptions{
        noCompress = "tflite"
    }
    
    
    buildFeatures{
        viewBinding = true
        mlModelBinding true
    }
    
   ```

Settings.gradle

  ```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven { url 'https://jitpack.io' }

    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }

    }
}
rootProject.name = "TestTf"
include ':app'

  ```

   
   MainActivity
   
```kotlin

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

```
 
 
  ```xml
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context=".MainActivity">

    <androidx.camera.view.PreviewView
        android:layout_width="match_parent"
        android:id="@+id/camera_preview"
        android:layout_height="match_parent"
        app:scaleType="fillStart"
        >
    </androidx.camera.view.PreviewView>

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:id="@+id/txtResult"
        android:textSize="30sp"
        android:textColor="@color/red"
        >
    </TextView>

    <Button
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:id="@+id/btnConectar"
        android:layout_alignParentBottom="true"
        android:text="Conectar Bt"
        >
    </Button>


    <ImageView
        android:layout_width="200dp"
        android:layout_height="200dp"
        android:id="@+id/imgBitMap"
        android:scaleType="fitStart"
        android:layout_alignParentEnd="true"
        >
    </ImageView>



</RelativeLayout>
  
  
   ```
 
 
 Interface
 
  ```kotlin
  interface ReturnInterpreter {

    fun classify(confidence:FloatArray,maxConfidence:Int)

}
  
   ```
   
   Class classify
   
```kotlin
    
    
import android.content.Context
import android.graphics.Bitmap
import com.ingenieriajhr.teachablemachine.MainActivity.Companion.INPUT_SIZE
import com.ingenieriajhr.teachablemachine.ml.ModelUnquant
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder


class ClassifyImageTf(context:Context) {


    //get instance model classifier image
    var modelUnquant = ModelUnquant.newInstance(context)


    lateinit var returnInterpreter: ReturnInterpreter

    fun listenerInterpreter(returnInterpreter: ReturnInterpreter){
        this.returnInterpreter = returnInterpreter
    }

    fun classify(img:Bitmap){
        val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, INPUT_SIZE, INPUT_SIZE, 3), DataType.FLOAT32)
        val byteBuffer = ByteBuffer.allocateDirect(4* INPUT_SIZE * INPUT_SIZE *3)
        byteBuffer.order(ByteOrder.nativeOrder())

        // get 1D array of 224 * 224 pixels in image
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)

        img!!.getPixels(intValues,0,img!!.width,0,0, img.width,img.height)

        // Reemplazar el bucle anidado con operaciones vectorizadas
        for (pixelValue in intValues) {
            byteBuffer.putFloat((pixelValue shr 16 and 0xFF) * (1f / 255f))
            byteBuffer.putFloat((pixelValue shr 8 and 0xFF) * (1f / 255f))
            byteBuffer.putFloat((pixelValue and 0xFF) * (1f / 255f))
        }
        inputFeature0.loadBuffer(byteBuffer)
        byteBuffer.clear()

        val output = modelUnquant.process(inputFeature0)
        val outputFeature = output.outputFeature0AsTensorBuffer
        val confidence = outputFeature.floatArray

        val maxPos = confidence.indices.maxByOrNull { confidence[it] }?:0

        returnInterpreter.classify(confidence, maxPos)
    }
}
```

![image](https://github.com/jose-jhr/Teachablemachine-arduino-android/assets/66834393/d467e28c-dafe-42c2-9c4c-971939d1ecba)
![image](https://github.com/jose-jhr/Teachablemachine-arduino-android/assets/66834393/1a9dc669-a174-437c-a0ad-af089cbaabba)

