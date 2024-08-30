package com.example.mindy_app

import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
//import androidx.activity.result.ActivityResultLauncher
//import androidx.activity.result.PickVisualMediaRequest
//import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.mindy_app.ui.theme.MindyappTheme
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest


var ip = "";
var port = 0;

class UdpDiscoveryTask : AsyncTask<Void, Void, UdpDiscoveryTask.ServerInfo?>() {

    data class ServerInfo(val name: String, val ip: String, val port: Int)

    override fun doInBackground(vararg params: Void): ServerInfo? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(12345)
            socket.broadcast = true

            var serverInfo = """{
                "name" : "ownMindy",
                "ip" : "0.0.0.0",
                "port" : 0
            }""".toByteArray()

            val packet = DatagramPacket(serverInfo, serverInfo.size,
                InetAddress.getByName("255.255.255.255"), 12345)

            Log.d("UdpDiscovery", "Listening for UDP broadcasts...")
            val receiveData = ByteArray(1024)
            val receivePacket = DatagramPacket(receiveData, receiveData.size)

            while (true) {
                socket.send(packet)
                socket.receive(receivePacket)

                val receivedData = String(receivePacket.data, 0, receivePacket.length)
                //Log.d("UdpDiscovery", "Received data: $receivedData")

                val jsonObject = JSONObject(receivedData)
                if(jsonObject.getString("name") == "Mindy") {
                    return ServerInfo(
                        jsonObject.getString("name"),
                        jsonObject.getString("ip"),
                        jsonObject.getInt("port")
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("UdpDiscovery", "Error during UDP discovery", e)
            return null
        } finally {
            socket?.close()
        }
    }

    override fun onPostExecute(result: ServerInfo?) {
        result?.let {
            Log.d("UdpDiscovery", "Discovered server: ${it.name} at ${it.ip}:${it.port}")
            if(it.name == "Mindy") {
                ip = it.ip;
                port = it.port;
            }
        } ?: Log.d("UdpDiscovery", "No server discovered")
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MindyappTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        UdpDiscoveryTask().execute()
        setContext(this)

        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) {
            val galleryUri = it
            selectedImages.clear()
            if(galleryUri.isNotEmpty()){
                selectedImages.addAll(galleryUri)
                processImages()
            }
        }
    }

    companion object {
        lateinit var galleryLauncher: ActivityResultLauncher<String>
        private var weakContext: WeakReference<Context>? = null

        fun setContext(context: Context) {
            weakContext = WeakReference(context)
        }

        fun getContext(): Context? {
            return weakContext?.get()
        }
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp)) {
        Button(onClick = { sendMessage("1") }, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text(text = "Abrir microfono")
        }
        Button(onClick = { sendMessage("0") }, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text(text = "Cerrar microfono")
        }
        Button(onClick = { sendMessage("2") }, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text(text = "Crear nuevo chat")
        }
        Button(onClick = { sendMessage("3") }, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text(text = "Banco mental")
        }
        Button(onClick = { sendMessage("4") }, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text(text = "Detener")
        }
        Button(onClick = { sendImages() }, modifier = Modifier.padding(top = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
            Text(text = "Adjuntar y enviar imagenes")
        }
    }
}

val selectedImages = mutableListOf<Uri>();

fun encoder(uri: Uri): String? {
    return try {
        val inputStream = MainActivity.getContext()?.contentResolver?.openInputStream(uri)
        val bytes = inputStream?.readBytes()
        inputStream?.close()

        if (bytes != null) {
            Base64.encodeToString(bytes, Base64.DEFAULT)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("Error", "Failed to encode file: ${e.message}")
        null
    }
}

fun sendImages(){
    MainActivity.galleryLauncher.launch("image/*")
}

fun processImages() {
    val processedImages = mutableListOf<String>()
    for (image: Uri in selectedImages) {
        val encodedImage = encoder(image)
        if (encodedImage != null) {
            processedImages.add(encodedImage)
        }
    }
    sendMessage("5")
    sendMessage(Gson().toJson(processedImages))
}

fun sendMessage(message: String) {
    if (ip.isNotEmpty() && port != 0) {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://$ip:$port").build()

        client.newWebSocket(request, object : WebSocketListener() {
            private var webSocket: WebSocket? = null

            override fun onOpen(ws: WebSocket, response: Response) {
                webSocket = ws
                ws.send(message)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                println("Received message: $text")
            }

            override fun onMessage(ws: WebSocket, bytes: ByteString) {
                println("Received bytes: ${bytes.hex()}")
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
                println("Closing: $code / $reason")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                t.printStackTrace()
            }
        })
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MindyappTheme {
        Greeting("Android")
    }
}
