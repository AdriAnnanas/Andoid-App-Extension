/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
* This file is a modified version of https://github.com/googlesamples/android-BluetoothLeGatt
* This Activity is started by the DeviceScanActivity and is passed a device name and address to start with
* Clicking on the listed device, opens up a page which allows connection and for scanning of GATT Services available for the device
* */
//package com.example.android.openbciBLE;
package com.mymind.gangliontablet.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import com.mymind.gangliontablet.R;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements View.OnClickListener {
    private final static String TAG = "OpenBCIBLE/"+ DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private static final int REQUEST_PERMISSION_WRITE_EXTERNAL_STORAGE = 1;
    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    private static BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyOnRead;
    private static BluetoothGattCharacteristic mGanglionSend;

    private boolean mIsDeviceGanglion= false;;

    private ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
    private  ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData= new ArrayList<ArrayList<HashMap<String, String>>>();

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private Button bStream;
    private Button b18bit;
    private Button bImpedance;

    //CSV File  will get saved in Documents/test/filename
    private File path;
    private String fileName= "ganglion_test.csv";



    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.i(TAG,"componentName: "+ componentName);
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            Log.v(TAG,"Trying to connect to GATTServer on: "+mDeviceName+" Address: "+mDeviceAddress );
            mBluetoothLeService.connect(mDeviceAddress);


        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.v(TAG,"Disconnecting from" );
            mBluetoothLeService = null;
        }
    };


    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.v(TAG,"GattServer Connected");
                mConnected = true;
                updateConnectionState(R.string.connected);

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.v(TAG,"GattServer Disconnected");
                mConnected = false;
                updateConnectionState(R.string.disconnected);

            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.v(TAG,"GattServer Services Discovered");
                // Show all the supported services and characteristics on the user interface.
               displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {


                String dataType = intent.getStringExtra(BluetoothLeService.DATA_TYPE);
                int[] sampleID;
                int[] sample1;
                int[] sample2;
                int[] accelerometer;


                switch (dataType){
                    /** SCALING FACTOR FOR DATA NOT YET INCLUDED
                     * CHECKOUT http://docs.openbci.com/Hardware/08-Ganglion_Data_Format
                     * ITNERPRETING THE EEG DATA
                     * EEG
                     * Scale Factor (Volts/count) = 1.2 Volts * 8388607.0 * 1.5 * 51.0;
                     * Accelerometer
                     * Accelerometer Scale Factor = 0.032;
                     */
                    case "RAW":
                        sampleID=intent.getIntArrayExtra(BluetoothLeService.SAMPLE_ID);
                        sample1=intent.getIntArrayExtra(BluetoothLeService.FULL_DATA_1);
                        sample2 = null;
                        Log.d("RAW",sampleID[0] + "," + sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] );
                       /* writetoCSV( path, fileName,
                                sampleID[0] + "," + sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] +"\n");
                        */break;

                    case "19BIT":
                        sampleID=intent.getIntArrayExtra(BluetoothLeService.SAMPLE_ID);
                        sample1=intent.getIntArrayExtra(BluetoothLeService.FULL_DATA_1);
                        sample2=intent.getIntArrayExtra(BluetoothLeService.FULL_DATA_2);
                        Log.d("19BIT",sampleID[0] + "," + sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] );
                        Log.d("19BIT",sampleID[1] + "," + sample2[0] + "," + sample2[1] + "," + sample2[2] + "," + sample2[3] );
                       /* writetoCSV( path, fileName,
                                sampleID[0] + "," + sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] +"\n" +
                                        sampleID[1] + "," + sample2[0] + "," + sample2[1] + "," + sample2[2] + "," + sample2[3] +"\n");*/
                        break;

                    case "18BIT":
                        sampleID=intent.getIntArrayExtra(BluetoothLeService.SAMPLE_ID);
                        sample1=intent.getIntArrayExtra(BluetoothLeService.FULL_DATA_1);
                        sample2=intent.getIntArrayExtra(BluetoothLeService.FULL_DATA_2);
                        accelerometer=intent.getIntArrayExtra(BluetoothLeService.ACCEL_DATA);

                        //If accelerometer is non null, we receive accelerometerdata in addition to the channels
                        if(accelerometer !=null){
                            Log.d("18BIT",sampleID[0] + "," + sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] );
                            Log.d("18BIT",sampleID[1] + "," + sample2[0] + "," + sample2[1] + "," + sample2[2] + "," + sample2[3] );
                            Log.d("Acc",sampleID[1] + "," + accelerometer[0] + "," + accelerometer[1] + "," + accelerometer[2] );
                            /*writetoCSV( path, fileName,
                                    sampleID[0] + "," + sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] +"\n" +
                                            sampleID[1] + "," + sample2[0] + "," + sample2[1] + "," + sample2[2] + "," + sample2[3]
                                            + "," + accelerometer[0] + "," + accelerometer[1] + "," + accelerometer[2]+"\n");*/
                        }
                        else{
                            Log.d("18BIT",sampleID[0] + "," + sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] );
                            Log.d("18BIT",sampleID[1] + "," + sample2[0] + "," + sample2[1] + "," + sample2[2] + "," + sample2[3] );
                           /* writetoCSV( path, fileName,
                                    sampleID[0] + "," + sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] +"\n" +
                                            sampleID[1] + "," + sample2[0] + "," + sample2[1] + "," + sample2[2] + "," + sample2[3] +"\n");*/
                        }

                        break;

                    case "IMPEDANCE":
                        sample1=intent.getIntArrayExtra(BluetoothLeService.IMPEDANCE);
                        Log.d("Impedance",sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] );
                       /* writetoCSV( path, fileName,
                                sample1[0] + "," + sample1[1] + "," + sample1[2] + "," + sample1[3] +"\n");*/
                        break;

                }
                }

        }
    };


   /* private boolean setCharacteristicNotification(BluetoothGattCharacteristic currentNotify, BluetoothGattCharacteristic newNotify, String toastMsg){
        if(currentNotify==null){//none registered previously
            mBluetoothLeService.setCharacteristicNotification(newNotify, true);
        }
        else {//something was registered previously
            if (!currentNotify.getUuid().equals(newNotify.getUuid())) {//we are subscribed to another characteristic?
                mBluetoothLeService.setCharacteristicNotification(currentNotify, false);//unsubscribe
                mBluetoothLeService.setCharacteristicNotification(newNotify, true); //subscribe to Receive
            }
            else{
                //no change required
                return false;
            }
        }
        Toast.makeText(getApplicationContext(), "Notify: "+toastMsg, Toast.LENGTH_SHORT).show();
        return true;//indicates reassignment needed for mNotifyOnRead
    }*/


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        path = getStorageDir("test");
        //this activity was started by another with data stored in an intent, process it
        final Intent intent = getIntent();

        //get the device name and address
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        //set flags if GANGLION is being used
        Log.v(TAG,"deviceName '"+mDeviceName+"'");
        if(mDeviceName!=null) {
            mIsDeviceGanglion = mDeviceName.toUpperCase().contains(SampleGattAttributes.DEVICE_NAME_GANGLION);
        }

        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);

        bStream=findViewById(R.id.toggle_stream);
        bStream.setOnClickListener(this);
        b18bit=findViewById(R.id.toggle_18bit);
        b18bit.setOnClickListener(this);
        bImpedance=findViewById(R.id.toggle_impedance);
        bImpedance.setOnClickListener(this);
       /* bStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
               if(mConnected){

                        char cmd = (char) mCommands[mCommandIdx];
                        Log.v(TAG,"Sending Command : "+cmd);
                        mGanglionSend.setValue(new byte[]{(byte)cmd});
                        mBluetoothLeService.writeCharacteristic((mGanglionSend));
                        mCommandIdx = (mCommandIdx +1)% mCommands.length; //update for next run to toggle off
                        Toast.makeText(getApplicationContext(), "Sent: '"+cmd+"' to Ganglion", Toast.LENGTH_SHORT).show();

                }
            }
        });*/

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);

        Log.v(TAG,"Creating Service to Handle all further BLE Interactions");
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {

            Log.v(TAG,"Trying to connect to: "+mDeviceName+" Address: "+mDeviceAddress);
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    public void onClick(View view) {

        switch (view.getId()) {
            case R.id.toggle_stream:
                if (mConnected) {
                    sendData(true);
                } else{
                    sendData(false);
                }
                break;
            case R.id.toggle_18bit:
                if (mConnected){
                    toggleAccelerometer(true);
                }
                break;
            case R.id.toggle_impedance:
                if (mConnected){
                    impedanceCheck(true);
                }
        }

    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }


    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);

        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            Log.w(TAG,"Service Iterator:"+gattService.getUuid());

            if(mIsDeviceGanglion){////we only want the SIMBLEE SERVICE, rest, we junk...
                if(!SampleGattAttributes.UUID_GANGLION_SERVICE.equals(gattService.getUuid().toString())) continue;
            }


            //Add Service data to gattServiceData
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);

                //if this is the read attribute for Cyton/Ganglion, register for notify service
                if(SampleGattAttributes.UUID_GANGLION_RECEIVE.equals(uuid)){//the RECEIVE characteristic
                    Log.v(TAG,"Registering notify for: "+uuid);
                    //we set it to notify, if it isn't already on notify
                    if(mNotifyOnRead==null){
                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        mNotifyOnRead = gattCharacteristic;
                    }
                    else{
                        Log.v(TAG, "De-registering Notification for: "+mNotifyOnRead.getUuid().toString() +" first");
                        mBluetoothLeService.setCharacteristicNotification(mNotifyOnRead, false);
                        mBluetoothLeService.setCharacteristicNotification(gattCharacteristic, true);
                        mNotifyOnRead = gattCharacteristic;
                    }
                }

                if(SampleGattAttributes.UUID_GANGLION_SEND.equals(uuid)){//the RECEIVE characteristic
                    Log.v(TAG,"GANGLION SEND:  "+uuid);
                    mGanglionSend=gattCharacteristic;
                }
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }


    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public static boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        Log.e("isExtStorageWritable", "Can´t write to Ext. Storage");
        return false;
    }

    public static void writetoCSV( File path, String Filename , String Data){

        if (isExternalStorageWritable()) {
            try {
                //Create a new file @ path/filename
                File file = new File(path, Filename);

                //1st Parameter = filepath, 2nd Paramter true=append
                FileWriter fw = new FileWriter(file, true);
                BufferedWriter out = new BufferedWriter(fw);
                out.write(Data);
                out.close();
                //toastie(context,"Entry Saved");
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
        else{
            Log.e("WriteToCsv", "Cannot write to storage!");
        }
    }

    public static File getStorageDir(String folderName) {
        // Get the directory for the user's public documents directory.
        isExternalStorageWritable();
        File documents = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);

        //path will be storage/sdcard/documents/foldername
        File path = new File(documents, folderName);
        Log.d("getStorageDir", path.toString());
        if (!path.mkdirs()) {
            Log.e("getStorageDir", "Directory not created");
        }
        return path;
    }




    public static  void sendData(boolean send){
        //b starts the stream, s stops it
        final byte[] mCommands = {'b','s'};
        char cmd;

        //if send is true, start the stream, if false send stop char
        if(send){
            cmd=(char) mCommands[0];
        }
        else{
            cmd=(char) mCommands[1];
        }

        Log.v(TAG,"Sending Command : "+cmd);
        mGanglionSend.setValue(new byte[]{(byte)cmd});
        mBluetoothLeService.writeCharacteristic((mGanglionSend));
        //  mCommandIdx = (mCommandIdx +1)% mCommands.length; //update for next run to toggle off
    }

    public static  void toggleAccelerometer(boolean send){
        //n activates the onboard Accelerometer, N deactivates it
        //when the Accelerometer is activated, the board will switch to 18 bit compression
        final byte[] mCommands = {'n','N'};
        char cmd;

        if(send){
            cmd=(char) mCommands[0];
        }
        else{
            cmd=(char) mCommands[1];
        }

        Log.v(TAG,"Sending Command : "+cmd);
        mGanglionSend.setValue(new byte[]{(byte)cmd});
        mBluetoothLeService.writeCharacteristic((mGanglionSend));
    }

    public static void impedanceCheck(boolean send){
        //z starts the stream, Z stops it
        final byte[] mCommands = {'z','Z'};
        char cmd;

        //if send is true, start the impedance check, if false send stop char
        if(send){
            cmd=(char) mCommands[0];
        }
        else{
            cmd=(char) mCommands[1];
        }

        Log.v(TAG,"Sending Command : "+cmd);
        mGanglionSend.setValue(new byte[]{(byte)cmd});
        mBluetoothLeService.writeCharacteristic((mGanglionSend));
    }

    public static void toggleChannel(boolean turnOffChannel, int channel){
        //Toggle Channels 1-4 on or off
        final byte[] mCommands;
        char cmd;
        switch(channel){
            case 1: mCommands = new byte[] {'1','!'};
                break;
            case 2: mCommands = new byte[] {'2','@'};
                break;
            case 3: mCommands = new byte[] {'3','#'};
                break;
            case 4: mCommands = new byte[] {'4','$'};
                break;
            default:
                Log.v(TAG,"Invalid channel : ");
                return;
        }

        //if send is true,the channel is turned off, if false it is turned on
        if(turnOffChannel){
            cmd=(char) mCommands[0];
        }
        else{
            cmd=(char) mCommands[1];
        }

        Log.v(TAG,"Sending Command : "+cmd);
        try {
            mGanglionSend.setValue(new byte[]{(byte)cmd});
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        mBluetoothLeService.writeCharacteristic((mGanglionSend));
    }

}
