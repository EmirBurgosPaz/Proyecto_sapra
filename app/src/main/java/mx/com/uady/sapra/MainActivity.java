package mx.com.uady.sapra;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public class MainActivity extends AppCompatActivity implements
        CompoundButton.OnCheckedChangeListener , AdapterView.OnItemClickListener, View.OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQ_ENABLE_BT  =   10;
    private static final int BT_BOUNDED     =   21;
    private static final int BT_SEARCH      =   22;
    private static final int REQUEST_CODE_LOC = 1 ;
    private static final int LED_GREEN        = 31 ;
    private static final int LED_RED          = 30 ;
    private static final int LED_BLUE         = 32;

    private FrameLayout frameMessage;
    private LinearLayout frameControls;

    private RelativeLayout frameLedControl;

    private  Button btnDisconnect;
    private  Switch switchGreen;
    private  Switch switchRed;
    private  Switch switchBlue;

    private Switch switchEnableBt;
    private Button btnEnableSearch;
    private ProgressBar pbProgress;
    private ListView listBtDevices;

    private BluetoothAdapter bluetoothAdapter;
    private BtlistAdapter listAdapter;
    private ArrayList<BluetoothDevice> bluetoothDevices;


    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private  FrameLayout frameBegin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        frameMessage  = findViewById(R.id.frame_message);
        frameControls = findViewById(R.id.frame_control);
        frameLedControl = findViewById(R.id.frameLedControl);

        switchEnableBt   = findViewById(R.id.switch_enable_bt);
        btnEnableSearch  = findViewById(R.id.btn_enable_search);
        pbProgress       = findViewById(R.id.pb_progress);
        listBtDevices    = findViewById(R.id.lv_bt_device);

        btnDisconnect = findViewById(R.id.btn_disconnect);
        switchGreen =     findViewById(R.id.switch_led_green);
        switchRed =       findViewById(R.id.switch_led_red);
        switchBlue =      findViewById(R.id.switch_led_blue);

        frameBegin      = findViewById(R.id.frame_Begin);

        switchEnableBt.setOnCheckedChangeListener(this);
        btnEnableSearch.setOnClickListener(this);
        listBtDevices.setOnItemClickListener(this);

        btnDisconnect.setOnClickListener(this);
        switchRed.setOnCheckedChangeListener(this);
        switchGreen.setOnCheckedChangeListener(this);
        switchBlue.setOnCheckedChangeListener(this);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        bluetoothDevices =  new ArrayList<>();

        /*
        * Brujeria
        * */

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

      /*Observa el estado de la conexion de bluetooth
      * */

        if (bluetoothAdapter == null) {
            Toast.makeText(this, getString(R.string.bluetooth_not_supported), Toast.LENGTH_SHORT).show();
            Log.d(TAG, "onCreate: " + getString(R.string.bluetooth_not_supported));
            finish();
        }

        if (bluetoothAdapter.isEnabled()) {
            showFrameControls();
            switchEnableBt.setChecked(true);
            setListAdapter(BT_BOUNDED);
            showFrameText();
        }
    }

    /*Metodos basicos de android estudio
    * */

    /*
    * onDestroy cierra el metodo de comunicacion entre la app y algun modulo de bluetooth constante*/

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);

        if(connectThread != null){
            connectThread.cancel();
        }

        if (connectedThread != null){
            connectedThread.cancel();
        }
    }

    /*
    * OnClick permite el uso del boton search (buscar dispostivos cercanos) y el de desconectar (Desconectar sirve se usa en la parte de la comunicacion del arduino)
    * */

    @Override
    public void onClick(View v) {

        if (v.equals(btnEnableSearch)){
            enableSearch();
        } else if (v.equals(btnDisconnect)){
            if (connectedThread != null){
                connectedThread.cancel();
            }

            if (connectThread != null){
                connectThread.cancel();
            }

            showFrameControls();
        }

    }

    /*Permite darle click al dispostivo que te aparezca en tu busqueda y unirlo ademas de gaurdarlo en tu lista de dispostivos

    Esto igual sirve para entrar a algun dispostivo si esta ya esta guardado
    * */

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (parent.equals(listBtDevices)){
                BluetoothDevice device = bluetoothDevices.get(position);
                if (device != null){
                    connectThread = new ConnectThread(device);
                    connectThread.start();
                }
            }
    }

    /*
    * Supervisa los cambios de estado de los switch y permite el prendido de los leds por le metodo de enableLed
    * */

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

        if (buttonView.equals(switchEnableBt)) {
            enableBt(isChecked);

            if (!isChecked) {
                showFrameMessage();
            }
        }else if (buttonView.equals(switchRed)){
            enableLed(LED_RED, isChecked);
        }else if (buttonView.equals(switchGreen)){
            enableLed(LED_GREEN, isChecked);
        }else if(buttonView.equals(switchBlue)){
            enableLed(LED_BLUE, isChecked);
        }

    }

    /*
    * Metodo por el cual se logra la comunicacion de bluetooth
    * */

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQ_ENABLE_BT) {
            if (resultCode == RESULT_OK && bluetoothAdapter.isEnabled()) {
                showFrameControls();
                setListAdapter(BT_BOUNDED);
            } else if (resultCode == RESULT_CANCELED) {
                enableBt(true);
            }
        }
    }


    /*
    * Metodos de carga para las diferentes pantallas de la app
    * */

    private void showFrameMessage (){
        frameMessage.setVisibility(View.VISIBLE);
        frameControls.setVisibility(View.GONE);
        frameLedControl.setVisibility(View.GONE);
        frameBegin.setVisibility(View.VISIBLE);
    }

    private void showFrameControls (){
        frameMessage.setVisibility(View.GONE);
        frameControls.setVisibility(View.VISIBLE);
        frameLedControl.setVisibility(View.GONE);
        frameBegin.setVisibility(View.GONE);
    }
    private void showFrameLEdControls (){
        frameMessage.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
        frameLedControl.setVisibility(View.VISIBLE);
        frameBegin.setVisibility(View.GONE);

    }

    private void showFrameText (){
        frameMessage.setVisibility(View.GONE);
        frameControls.setVisibility(View.GONE);
        frameLedControl.setVisibility(View.VISIBLE);
        frameBegin.setVisibility(View.GONE);
    }

    /*
     * Te permite conectarte al bluetooth
     * */

    private void enableBt(boolean flag) {
        if (flag) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQ_ENABLE_BT);
        } else {
            bluetoothAdapter.disable();
        }
    }

    /*
    * Metodo que nos permite dibujar la lista de dispositvos conectados y las imagenes de al lado, junyo con la clase BtListAdapter
    * */

    private  void  setListAdapter (int type){

        bluetoothDevices.clear();
        int iconType = R.drawable.ic_bluetooth_bounded_device;

        switch ( type){
            case   BT_BOUNDED:
                bluetoothDevices = getBoundedDevices();
                iconType = R.drawable.ic_bluetooth_bounded_device;
                break;

            case BT_SEARCH:
                iconType = R.drawable.ic_bluetooth_search_device;
                break;
        }
        listAdapter = new BtlistAdapter(this,bluetoothDevices, iconType);
        listBtDevices.setAdapter(listAdapter);

    }

    private  ArrayList<BluetoothDevice> getBoundedDevices(){
        Set<BluetoothDevice> deviceSet = bluetoothAdapter.getBondedDevices();
        ArrayList<BluetoothDevice> tmpArrayList = new ArrayList<>();
        if (deviceSet.size()>0){
            for (BluetoothDevice device : deviceSet ){
                tmpArrayList.add(device );

            }
        }

        return tmpArrayList;
    }

    /*
    * Supervision de la busqueda de dispositvos
    * */

    private  void  enableSearch (){
        if (bluetoothAdapter.isDiscovering()){
            bluetoothAdapter.cancelDiscovery();
        }else {
            accessLocationPermission();
            bluetoothAdapter.startDiscovery();
        }
    }

    /*
    * Metodo de busqueda de dispositivos
    * */

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            final   String action = intent.getAction();

            switch (action){
                case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                    btnEnableSearch.setText(getString(R.string.stop_search));
                    pbProgress.setVisibility(View.VISIBLE);
                    setListAdapter(BT_SEARCH);
                    break;

                case  BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                    btnEnableSearch.setText(getString(R.string.Search));
                    pbProgress.setVisibility(View.GONE);
                    break;

                case  BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null){
                        bluetoothDevices.add(device);
                        listAdapter.notifyDataSetChanged();
                    }
                    break;
            }
        }
    };

    /*
    * Permisos esenciales para Marshmallow
    * */

    private void accessLocationPermission() {
        int accessCoarseLocation = this.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        int accessFineLocation   = this.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);

        List<String> listRequestPermission = new ArrayList<String>();

        if (accessCoarseLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (accessFineLocation != PackageManager.PERMISSION_GRANTED) {
            listRequestPermission.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!listRequestPermission.isEmpty()) {
            String[] strRequestPermission = listRequestPermission.toArray(new String[listRequestPermission.size()]);
            this.requestPermissions(strRequestPermission, REQUEST_CODE_LOC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_LOC:

                if (grantResults.length > 0) {
                    for (int gr : grantResults) {
                        // Check if request is granted or not
                        if (gr != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                    }
                }
                break;
            default:
                return;
        }
    }

    /*
    * Magia negra y brujeria
    * */

    private class ConnectThread  extends Thread{

        private BluetoothSocket bluetoothSocket = null;
        private  Boolean  success = false;

        public  ConnectThread(BluetoothDevice device){
            try{
            Method method = device.getClass().getMethod("createRfcommSocket", new  Class[]{int.class});
            bluetoothSocket = (BluetoothSocket) method.invoke(device, 1);
            } catch (Exception e){
                e.printStackTrace();
            }

        }

        @Override
        public void run() {
            try {
                bluetoothSocket.connect();
                success =  true;
            }catch (IOException e){
                e.printStackTrace();

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "No se puede conectar", Toast.LENGTH_SHORT).show();
                    }
                });
                cancel();
            }
            if ( success){

                connectedThread = new ConnectedThread(bluetoothSocket);
                connectedThread.start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                showFrameLEdControls();
                    }
                });
            }
        }

        public  boolean isConnect(){
            return  bluetoothSocket.isConnected();
        }

        public  void  cancel(){
            try {
                bluetoothSocket.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private  class ConnectedThread extends Thread{

        private final InputStream inputStream;
        private final OutputStream outputStream;

        private ConnectedThread(BluetoothSocket bluetoothSocket) {
            InputStream inputStream = null;
            OutputStream outputStream = null;

            try{
                inputStream = bluetoothSocket.getInputStream();
                outputStream = bluetoothSocket.getOutputStream();
            }catch (IOException e){
                e.printStackTrace();
            }

            this.inputStream= inputStream;
            this.outputStream = outputStream;
        }

        @Override
        public void run() {

        }

        public  void  write(String command){
            byte[] bytes = command.getBytes();
            if (outputStream != null){
                try{
                outputStream.write(bytes);
                outputStream.flush();
            }catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        public  void  cancel(){
            try {
                inputStream.close();
                outputStream.close();
            }catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    /*
    * Metodo escalable para mandar informacion y permitiir que al arduino pueda prender el led o lo que se pida
    * */

    private void enableLed(int led, boolean state) {
        if (connectedThread != null && connectThread.isConnect()){
            String command = "";
            switch(led){
                case LED_RED:
                    command = (state)  ? "red on#" : "red off#" ;
                    break;
                case LED_GREEN:
                    command = (state)  ? "green on#" : "green off#";
                    break;
                case LED_BLUE:
                    command = (state)  ? "blue on#" : "blue off#";
                    break;
            }

            connectedThread.write(command);
        }
    }


}
