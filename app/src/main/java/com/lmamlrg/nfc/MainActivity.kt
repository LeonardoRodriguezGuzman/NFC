package com.lmamlrg.nfc

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.PatternMatcher
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    //Variables a usar
    private var selectedImage: Uri? = null
    private lateinit var nfcAdapter: NfcAdapter
    private val PERMISSION_REQUEST_CODE = 1
    private val PICK_IMAGE_REQUEST_CODE = 2
    private lateinit var btnSeleccionar: Button
    private lateinit var btnCompartir: Button
    private lateinit var btnRecibir: Button
    private lateinit var imagenSeleccionada: ImageView
    private lateinit var imagenRecibida: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        solicitarPermisos()
        btnSeleccionar = findViewById(R.id.btnSeleccionaImg)
        btnCompartir = findViewById(R.id.btnCompartir)
        btnRecibir = findViewById(R.id.btnRecibir)
        imagenSeleccionada = findViewById(R.id.imagenSeleccionada)
        imagenRecibida = findViewById(R.id.imagenRecibida)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(
                this,
                "NFC está desactivado. Por favor activa NFC y vuelve a intentarlo.",
                Toast.LENGTH_SHORT
            ).show()
        }

        btnSeleccionar.setOnClickListener {
            seleccionarImagen()
        }

        btnCompartir.setOnClickListener {
            compartirImagenNFC()
        }

        btnRecibir.setOnClickListener {
            // Crear un objeto PendingIntent para la actividad actual
            val pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0
            )
            // Crear un objeto IntentFilter para filtrar las etiquetas NFC que contengan imágenes
            val intentFilter = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
            val mimeTypes = arrayOf("image/jpeg", "image/png")
            intentFilter.addDataType("*/*")
            intentFilter.addDataScheme("http")
            intentFilter.addDataAuthority("*", null)
            intentFilter.addDataPath("*", PatternMatcher.PATTERN_SIMPLE_GLOB)
            nfcAdapter.enableForegroundDispatch(
                this,
                pendingIntent,
                arrayOf(intentFilter),
                arrayOf(mimeTypes)
            )
            // Mostrar un mensaje al usuario indicando que el dispositivo está listo para recibir la imagen
            Toast.makeText(
                this,
                "Acerca el dispositivo a otra etiqueta NFC para recibir la imagen.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    //Seleccionar Imagen
    private fun seleccionarImagen() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, PICK_IMAGE_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            selectedImage = data.data // Guardamos la Uri de la imagen seleccionada
            imagenSeleccionada.setImageURI(selectedImage) // Mostramos la imagen en el ImageView
        }
    }

    //NFC
    private fun compartirImagenNFC() {
        // Verifica si se ha seleccionado una imagen
        if (selectedImage == null) {
            Toast.makeText(this, "Por favor selecciona una imagen primero.", Toast.LENGTH_SHORT)
                .show()
            return
        }
        // Convierte la imagen a un arreglo de bytes
        val inputStream = selectedImage?.let { contentResolver.openInputStream(it) }
        val bitmap = BitmapFactory.decodeStream(inputStream)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        // Verifica si el dispositivo tiene NFC
        val adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta NFC", Toast.LENGTH_SHORT).show()
            return
        }
        // Verifica si NFC está activado en el dispositivo
        if (!adapter.isEnabled) {
            Toast.makeText(
                this,
                "NFC está desactivado. Por favor activa NFC y vuelve a intentarlo.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        // Crea un intent para compartir los datos a través de NFC
        val intent = Intent(this, javaClass).apply {
            action = Intent.ACTION_SEND
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, byteArray)
        }
        // Crea un PendingIntent para la actividad actual
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, 0)
        // Crea un objeto NdefMessage con el array de bytes
        val ndefRecord = NdefRecord.createMime("image/jpeg", byteArray)
        val ndefMessage = NdefMessage(ndefRecord)
        // Habilita la lectura y escritura de NFC en el adaptador
        adapter.enableForegroundDispatch(this, pendingIntent, null, null)
        // Escribir el mensaje en la etiqueta NFC
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        val ndef = Ndef.get(tag)
        ndef.connect()
        ndef.writeNdefMessage(ndefMessage)
        ndef.close()
        // Deshabilitar la lectura y escritura de NFC en el adaptador
        adapter.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Verificar si la acción es ACTION_NDEF_DISCOVERED
        if (intent.action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            // Obtener el array de bytes de la imagen recibida
            val byteArray =
                intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.get(0)?.let {
                    (it as NdefMessage).records[0].payload
                }
            // Convertir el array de bytes a un objeto Bitmap
            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray?.size ?: 0)
            // Mostrar la imagen en el ImageView
            imagenRecibida.setImageBitmap(bitmap)
            // Deshabilitar la recepción de NFC
            nfcAdapter.disableForegroundDispatch(this)
        }
    }

    private fun solicitarPermisos() {
        val permissionNFC = Manifest.permission.NFC
        val permissionWrite = Manifest.permission.WRITE_EXTERNAL_STORAGE
        val permissionRead = Manifest.permission.READ_EXTERNAL_STORAGE
        val permissionList = arrayOf(permissionNFC, permissionWrite, permissionRead)
        requestPermissions(permissionList, PERMISSION_REQUEST_CODE)
    }
}
