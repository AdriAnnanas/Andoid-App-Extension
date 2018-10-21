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

package com.mymind.gangliontablet.bluetooth;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {

    private final static String TAG = "Ganglion"+BluetoothLeService.class.getSimpleName();


    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED ="com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED ="com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED ="com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE ="com.example.bluetooth.le.ACTION_DATA_AVAILABLE";

    public final static String FULL_DATA_1 ="com.example.bluetooth.le.FULL_DATA_1";
    public final static String FULL_DATA_2 ="com.example.bluetooth.le.FULL_DATA_2";
    public final static String IMPEDANCE ="com.example.bluetooth.le.IMPEDANCE";
    public final static String ACCEL_DATA ="com.example.bluetooth.le.IMPEDANCE";

    public final static String DATA_TYPE ="com.example.bluetooth.le.DATA_TYPE";
    public final static String SAMPLE_ID ="com.example.bluetooth.le.SAMPLE_ID";


    public final static UUID UUID_GANGLION_RECEIVE = UUID.fromString(SampleGattAttributes.UUID_GANGLION_RECEIVE);
    private static int last_id;
    private static int[] lastChannelData = {0, 0, 0, 0};
    private static int[] lastAccelerometer = {0, 0, 0};
    private static int[] lastImpedance = {0, 0, 0, 0, 0};
    private static int[] sample_id={0, 0};
    private static int[][] fullData = {{0, 0, 0, 0},{0, 0, 0, 0}};
    private static int packetID;
    private static int lostPackets=0;
    private static int totalPackets=0;

    private static int counter=0;
    private static boolean accSampleComplete=false;

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.v(TAG,"GattServer Services Discovered");
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                gatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
    /**
     * BEGIN OF CUSTOM METHODS
     */

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        //Log.d(TAG, "UUID: " + characteristic.getUuid());

        if (UUID_GANGLION_RECEIVE.equals(characteristic.getUuid())) {
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                parseData(data);
                if (packetID == 0) {
                    intent.putExtra(DATA_TYPE, "RAW");
                    intent.putExtra(FULL_DATA_1, fullData[0]);
                    intent.putExtra(SAMPLE_ID, sample_id);
                } else if (packetID >=1 && packetID <= 100){

                    intent.putExtra(DATA_TYPE, "18BIT");
                    intent.putExtra(FULL_DATA_1, fullData[0]);
                    intent.putExtra(FULL_DATA_2, fullData[1]);
                    intent.putExtra(SAMPLE_ID, sample_id);
                    if(accSampleComplete)
                        intent.putExtra(ACCEL_DATA, lastAccelerometer);
                } else if (packetID >=101 && packetID <= 200){
                    intent.putExtra(DATA_TYPE, "19BIT");
                    intent.putExtra(FULL_DATA_1, fullData[0]);
                    intent.putExtra(FULL_DATA_2, fullData[1]);
                    intent.putExtra(SAMPLE_ID, sample_id);
                } else if (packetID >= 201 && packetID <= 205){
                    //only send the impedance data when all channels are available
                    if(packetID == 205){
                        intent.putExtra(DATA_TYPE, "IMPEDANCE");
                        intent.putExtra(IMPEDANCE, lastImpedance);
                    }else{
                        intent.putExtra(DATA_TYPE, "INCOMPLETE");

                    }
                    //TODO count number of Impedance Checks?
                } else {
                    intent.putExtra(DATA_TYPE, "INVALID");
                }
            }
        } else {
            // Handle other characteristics here
        }
        sendBroadcast(intent);
    }


    private static boolean parseData(byte[] data) {

        //Convert from -128 - 127 to 0 - 255
        if (data[0] < 0) {
            packetID = data[0] + 256;
        } else {
            packetID = data[0];
        }
        //separate the data
        byte[] payload= Arrays.copyOfRange(data, 1, data.length);
        //  Log.wtf(TAG," PacketID " + packetID);
        //   Log.wtf(TAG," Byte 1: " + payload[0] +" Byte 2: " + payload[1] + " Byte 3: " + payload[2] +" Byte 4: " + payload[3] +
        //           " Byte 5: " + payload[4] +" Byte 6: " + payload[5] +" Byte 7: " + payload[6] +" Byte 8: " + payload[7] +
        //            " Byte 9: " + payload[8] +" Byte 10: " + payload[9] + " Byte 11: " + payload[10] +" Byte 12: " + payload[11] +
        //            " Byte 13: " + payload[12] +" Byte 14: " + payload[13] +" Byte 15: " + payload[14] +" Byte 16: " + payload[15] +
        //           " Byte 17: " + payload[16] +" Byte 18: " + payload[17] +" Byte 19: " + payload[18]);
        //Boolean receiving_ASCII;
        if (packetID == 0) {
            //receiving_ASCII = false;
            parseRaw(packetID, payload);
            // 18-bit compression with Accelerometer
        } else if (packetID >= 1 && packetID <= 100){
            //receiving_ASCII = false;
            parse18bit(packetID, payload);
            //19-bit compression without Accelerometer
        } else if(packetID >=101 && packetID <= 200){
            //receiving_ASCII = false;
            //packetID-100 for sampleID calculation
            parse19bit(packetID-100, payload);
            //Impedance Channel
        } else if (packetID >= 201 && packetID <= 205){
            // receiving_ASCII = false;
            parseImpedance(packetID, payload);
            //Part of ASCII
        } else if( packetID == 206){
            String byteString = new String(payload);
            Log.wtf(TAG, "Packet 206 : " + byteString + "\n");
            //receiving_ASCII = true;
            //End of ASCII message
        } else if (packetID == 207){
            String byteString = new String(payload);
            Log.wtf(TAG, "Packet 207 : " + byteString + "\n");
            //receiving_ASCII = false;
        } else{
            Log.wtf(TAG, "Warning: unknown packet type : " + packetID);
            return false;
        }
        return true;
    }

    private static void parse18bit(int packetID, byte[] payload){

        // Dealing with "18-bit compression without Accelerometer" """
        if (payload.length != 19){
            Log.e(TAG, "Wrong size, for Raw data " + payload.length + " instead of 19 bytes");
            return;
        }

        //the last byte of every packetID ending with x1,x2,x3 contain the x,y,z accelerometer data
        // accelerometer X
        if( packetID % 10 == 1){
            lastAccelerometer[0] = payload[18];
            counter=1;
        }
        // accelerometer Y
        else if( packetID % 10 == 2){
            lastAccelerometer[1] = payload[18];
            counter++;
        }
        // accelerometer Z
        else if( packetID % 10 == 3){
            lastAccelerometer[2] = payload[18];
            //if no packets are lost, the accel data can be sent
            if(counter==2){
                accSampleComplete=true;
            }

        } else{
            //prevent sending incorrect samples due to packet loss
            accSampleComplete=false;
            counter=0;
        }

        //Sending all 19 bytes to save performance by not copying the array
        //byte[] payload18bit= Arrays.copyOfRange(payload, 1, payload.length-1);
        int[][] deltas = decompressDeltas18Bit(payload);
        // the sample_id will be shifted
        int delta_id = 0;

        for ( int i = 0; i < 2; i++ ) {
            for ( int j = 0; j < 4; j++ ) {
                fullData[i][j]=lastChannelData[j] - deltas[i][j];
                lastChannelData[j]=fullData[i][j];
            }
            //convert from packet to sample id (2 samples per packet)
            sample_id[delta_id] = packetID * 2 + delta_id;
            //18bit packets hold deltas between two samples
            delta_id++;
        }
        // Log.wtf(TAG, "Channel 1 " + fullData[0][0] + " Channel 2 " + fullData[0][1] + " Channel 3 " + fullData[0][2] + " Channel 4 " + fullData[0][3]);
        //  Log.wtf(TAG, "Channel 1 " + fullData[1][0] + " Channel 2 " + fullData[1][1] + " Channel 3 " + fullData[1][2] + " Channel 4 " + fullData[1][3]);
        updatePacketsCount(packetID);
    }


    private static void parseImpedance(int packetID, byte[] payload){

        //Dealing with Impedance check
        if(payload[payload.length-1] != 'Z' ){
            Log.e(TAG, "Wrong format for impedance check, should be ASCII ending with 'Z\\n'");
        }

        //Remove the stop characters
        byte[] bytes= Arrays.copyOfRange(payload, 0, payload.length-2);
        //convert from ASCII to actual value in kOhm
        String Impedance = new String(bytes);
        switch(packetID){
            case 201:
                lastImpedance[0]=Integer.parseInt(Impedance);
                break;
            case 202:
                lastImpedance[1]=Integer.parseInt(Impedance);
                break;
            case 203:
                lastImpedance[2]=Integer.parseInt(Impedance);
                break;
            case 204:
                lastImpedance[3]=Integer.parseInt(Impedance);
                break;
            case 205:
                lastImpedance[4]=Integer.parseInt(Impedance);
                break;
            default:
                break;
        }

    }

    private static void parseRaw(int packetID, byte[] payload){
        //Dealing with "Raw uncompressed" - 24 Bit
        if (payload.length != 19){
            Log.e(TAG, "Wrong size, for Raw data " + payload.length + " instead of 19 bytes");
            return;
        }
        // 4 channels of 24bits - 4*3=12 Bytes of 19 Bytes used
        //Take values one by one
        for(int i=0;  i<12;  i=i+3){
            lastChannelData[i/3]=conv24bitsToInt(Arrays.copyOfRange(payload, i, i+3));
            fullData[0][i/3]=lastChannelData[i/3];
            fullData[1][i/3]=0;
        }
        // Log.wtf(TAG,"RAW\n");
        // Log.wtf(TAG, "Channel 1 " + fullData[0][0] + " Channel 2 " + fullData[0][1] + " Channel 3 " + fullData[0][2] + " Channel 4 " + fullData[0][3]);
        sample_id[0]=1;
        sample_id[1]=0;
        updatePacketsCount(packetID);
    }

    private static void parse19bit(int packetID, byte[] payload){
        //Dealing with "19-bit compression without Accelerometer"

        if (payload.length != 19){
            Log.e(TAG, "Wrong size, for 19-bit compression data " + payload.length + " instead of 19 bytes");
            return ;
        }
        //should get 2 by 4 arrays of uncompressed data
        int[][] deltas = decompressDeltas19Bit(payload);
        // the sample_id will be shifted
        int delta_id = 0;

        for ( int i = 0; i < 2; i++ ) {
            for ( int j = 0; j < 4; j++ ) {
                fullData[i][j]=lastChannelData[j] - deltas[i][j];
                lastChannelData[j]=fullData[i][j];
            }
            //convert from packet to sample id
            sample_id[delta_id] = packetID * 2 + delta_id;
            //19bit packets hold deltas between two samples
            delta_id++;
        }
        updatePacketsCount(packetID);
        // Log.wtf(TAG, "Channel 1 " + fullData[0][0] + " Channel 2 " + fullData[0][1] + " Channel 3 " + fullData[0][2] + " Channel 4 " + fullData[0][3]);
        // Log.wtf(TAG, "Channel 1 " + fullData[1][0] + " Channel 2 " + fullData[1][1] + " Channel 3 " + fullData[1][2] + " Channel 4 " + fullData[1][3]);
    }

    private static void updatePacketsCount(int packetID){
        // Update last packet ID and dropped packets
        totalPackets++;
        int packets_dropped;
        if (last_id == -1){
            last_id = packetID;
            return;
        }
        // ID loops every 101 packets
        if (packetID > last_id){
            packets_dropped = packetID - last_id - 1;
        } else{
            packets_dropped = packetID + 101 - last_id - 1;
        }
        last_id = packetID;
        if (packets_dropped > 0){
            if (packets_dropped==100)
                return;
            Log.e(TAG, "Warning: dropped " + packets_dropped + " packets.");
            lostPackets = lostPackets + packets_dropped;
        }
    }

    public static void getPacketLoss(){
        int packetNumber = lostPackets + totalPackets;
        double packetLoss = (double)lostPackets*100/packetNumber;
        Log.wtf(TAG, "Lost: " + lostPackets + " out of " + packetNumber + "\n % of loss= " + packetLoss);
    }

    private static int[][] decompressDeltas18Bit(byte[] payload){
        /*  Called to when a compressed packet is received.
        payload: the data portion of the sample. So 19 bytes.
        return {Array} - An array of deltas of shape 2x4 (2 samples per packet and 4 channels per sample.) */
        if (payload.length != 19){
            Log.e(TAG,"Input should be 19 bytes long.");
        }

        int[][] receivedDeltas= {{0, 0, 0, 0},
                {0, 0, 0, 0}};

        //Sample 1 - Channel 1
        int[] miniBuf = {
                ( (payload[0] & 0xFF) >>> 6),
                (((payload[0] & 0x3F) << 2) & 0xFF) | ( (payload[1] & 0xFF) >>> 6),
                (((payload[1] & 0x3F) << 2) & 0xFF) | ( (payload[2] & 0xFF) >>> 6)
        };
        receivedDeltas[0][0] = conv18bitToInt32( miniBuf);

        //Sample 1 - Channel 2
        miniBuf = new int[]{
                (payload[2] & 0x3F) >>> 4,
                (payload[2] << 4 & 0xFF) | ( (payload[3] & 0xFF) >>> 4),
                (payload[3] << 4 & 0xFF) | ( (payload[4] & 0xFF) >>> 4)
        };
        receivedDeltas[0][1] = conv18bitToInt32(miniBuf);

        //Sample 1 - Channel 3
        miniBuf = new int[]{
                ((payload[4] & 0x0F) >>> 2),
                (payload[4] << 6 & 0xFF) | ( (payload[5] & 0xFF) >>> 2),
                (payload[5] << 6 & 0xFF) | ( (payload[6] & 0xFF) >>> 2)
        };
        receivedDeltas[0][2] = conv18bitToInt32(miniBuf);

        //Sample 1 - Channel 4
        miniBuf = new int[]{
                (payload[6] & 0x03),
                (payload[7] & 0xFF),
                (payload[8] & 0xFF)
        };
        receivedDeltas[0][3] = conv18bitToInt32(miniBuf);

        //Sample 2 - Channel 1
        miniBuf = new int[]{
                ( (payload[9] & 0xFF) >>> 6),
                (((payload[9] & 0x3F) << 2) & 0xFF) | ( (payload[10] & 0xFF) >>> 6),
                (((payload[10] & 0x3F) << 2) & 0xFF) | ( (payload[11] & 0xFF) >>> 6)
        };
        receivedDeltas[1][0] = conv18bitToInt32(miniBuf);

        //Sample 2 - Channel 2
        miniBuf = new int[]{
                (payload[11] & 0x3F) >>> 4,
                (payload[11] << 4 & 0xFF) | ( (payload[12] & 0xFF) >>> 4),
                (payload[12] << 4 & 0xFF) | ( (payload[13] & 0xFF) >>> 4)
        };
        receivedDeltas[1][1] = conv18bitToInt32(miniBuf);

        // Sample 2 - Channel 3
        miniBuf = new int[]{
                ((payload[13] & 0x0F) >>> 2),
                (payload[13] << 6 & 0xFF) | ( (payload[14] & 0xFF) >>> 2),
                (payload[14] << 6 & 0xFF) | ( (payload[15] & 0xFF) >>> 2)
        };
        receivedDeltas[1][2] = conv18bitToInt32(miniBuf);

        // Sample 2 - Channel 4
        miniBuf = new int[]{
                (payload[15] & 0x03),
                (payload[16] & 0xFF),
                (payload[17] & 0xFF)
        };
        receivedDeltas[1][3] = conv18bitToInt32(miniBuf);

        return receivedDeltas;
    }

    private static int[][] decompressDeltas19Bit(byte[] payload){
     /*  Called to when a compressed packet is received.
        payload: the data portion of the sample. So 19 bytes.
        return {Array} - An array of deltas of shape 2x4 (2 samples per packet and 4 channels per sample.) */
        if (payload.length != 19){
            Log.e(TAG,"Input should be 19 bytes long.");
        }

        int[][] receivedDeltas= {{0, 0, 0, 0},
                {0, 0, 0, 0}};

        //Sample 1 - Channel 1
        int[] miniBuf = {
                ( (payload[0] & 0xFF) >>> 5),
                (((payload[0] & 0x1F) << 3) & 0xFF) | ( (payload[1] & 0xFF) >>> 5),
                (((payload[1] & 0x1F) << 3) & 0xFF) | ( (payload[2] & 0xFF) >>> 5)
        };
        receivedDeltas[0][0] = conv19bitToInt32( miniBuf);

        //Sample 1 - Channel 2
        miniBuf = new int[]{
                (payload[2] & 0x1F) >>> 2,
                (payload[2] << 6 & 0xFF) | ( (payload[3] & 0xFF) >>> 2),
                (payload[3] << 6 & 0xFF) | ( (payload[4] & 0xFF) >>> 2)
        };

        receivedDeltas[0][1] = conv19bitToInt32(miniBuf);

        //Sample 1 - Channel 3
        miniBuf = new int[]{
                ((payload[4] & 0x03) << 1 & 0xFF) | ( (payload[5] & 0xFF) >>> 7),
                ((payload[5] & 0x7F) << 1 & 0xFF) | ( (payload[6] & 0xFF) >>> 7),
                ((payload[6] & 0x7F) << 1 & 0xFF) | ( (payload[7] & 0xFF) >>> 7)
        };
        receivedDeltas[0][2] = conv19bitToInt32(miniBuf);

        //Sample 1 - Channel 4
        miniBuf = new int[]{
                ((payload[7] & 0x7F) >>> 4),
                ((payload[7] & 0x0F) << 4 & 0xFF) | ( (payload[8] & 0xFF) >>> 4),
                ((payload[8] & 0x0F) << 4 & 0xFF) | ( (payload[9] & 0xFF) >>> 4)
        };
        receivedDeltas[0][3] = conv19bitToInt32(miniBuf);

        //Sample 2 - Channel 1
        miniBuf = new int[]{
                ((payload[9] & 0x0F) >>> 1),
                (payload[9] << 7 & 0xFF) | ( (payload[10] & 0xFF) >>> 1),
                (payload[10] << 7 & 0xFF) | ( (payload[11] & 0xFF) >>> 1)
        };
        receivedDeltas[1][0] = conv19bitToInt32(miniBuf);

        //Sample 2 - Channel 2
        miniBuf = new int[]{
                ((payload[11] & 0x01) << 2 & 0xFF) | ( (payload[12] & 0xFF) >>> 6),
                (payload[12] << 2 & 0xFF) | ( (payload[13] & 0xFF) >>> 6),
                (payload[13] << 2 & 0xFF) | ( (payload[14] & 0xFF) >>> 6)
        };
        receivedDeltas[1][1] = conv19bitToInt32(miniBuf);

        // Sample 2 - Channel 3
        miniBuf = new int[]{
                ((payload[14] & 0x38) >>> 3),
                ((payload[14] & 0x07) << 5 & 0xFF) | ((payload[15] & 0xF8) >>> 3),
                ((payload[15] & 0x07) << 5 & 0xFF) | ((payload[16] & 0xF8) >>> 3)
        };
        receivedDeltas[1][2] = conv19bitToInt32(miniBuf);

        // Sample 2 - Channel 4
        miniBuf = new int[]{
                (payload[16] & 0x07),
                (payload[17] & 0xFF),
                (payload[18] & 0xFF)};
        receivedDeltas[1][3] = conv19bitToInt32(miniBuf);

        return receivedDeltas;
    }

    private static int conv18bitToInt32(int[] threeByteBuffer) {
        // Convert 19bit data coded on 3 bytes to a proper integer (LSB bit 1 used as sign). """
        if (threeByteBuffer.length != 3) {
            Log.e(TAG, "Input should be 3 bytes long.");
            return -1;
        }
        int prefix = 0b0000000000000;
        //if LSB is 1, negative number, some hasty unsigned to signed conversion to do
        if ((threeByteBuffer[2] & 0x01 ) > 0) {
            prefix = 0b11111111111111;
            return ((prefix << 18) | (threeByteBuffer[0] << 16) | (threeByteBuffer[1] << 8) | threeByteBuffer[2]);
        } else {
            return ((prefix << 18) | (threeByteBuffer[0] << 16) | (threeByteBuffer[1] << 8) | threeByteBuffer[2]);
        }
    }

    private static int conv19bitToInt32(int[] threeByteBuffer) {
        // Convert 19bit data coded on 3 bytes to a proper integer (LSB bit 1 used as sign). """
        if (threeByteBuffer.length != 3) {
            Log.e(TAG, "Input should be 3 bytes long.");
            return -1;
        }
        int prefix = 0b0000000000000;
        //if LSB is 1, negative number, some hasty unsigned to signed conversion to do
        if ((threeByteBuffer[2] & 0x01 ) > 0) {
            prefix = 0b1111111111111;
            return ((prefix << 19) | (threeByteBuffer[0] << 16) | (threeByteBuffer[1] << 8) | threeByteBuffer[2]);
        } else {
            return ((prefix << 19) | (threeByteBuffer[0] << 16) | (threeByteBuffer[1] << 8) | threeByteBuffer[2]);
        }
    }

    private static int conv24bitsToInt(byte[] byteArray){
        //Convert 24bit data coded on 3 bytes to a proper integer """
        if (byteArray.length != 3){
            // Log.e(TAG, "Input should be 3 bytes long.");
            return -1;
        }

        int newInt = (((0xFF & byteArray[0]) << 16) | ((0xFF & byteArray[1]) << 8) | (0xFF & byteArray[2])
        );

        //If the MSB is 1 then the number is negative - fill up with 1s
        if ((newInt & 0x00800000) > 0) {
            newInt |= 0xFF000000;
        } else {
            newInt &= 0x00FFFFFF;
        }

        return newInt;
    }


    /**
     * END OF CUSTOM METHODS**************************************************************************
     * END OF CUSTOM METHODS**************************************************************************
     * END OF CUSTOM METHODS**************************************************************************
     */

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        Log.v(TAG,"Connecting to GATT Server on the Device");
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    public void onCharacteristicWrite (BluetoothGatt gatt,
                                       BluetoothGattCharacteristic characteristic,
                                       int status){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.w(TAG, "Written to: " +characteristic.getUuid() + " Status: "+ (mBluetoothGatt.GATT_SUCCESS==status));
    }

    public void writeCharacteristic(BluetoothGattCharacteristic characteristic){
        //pre-prepared characteristic to write to
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        Log.w(TAG, "Writing to " +characteristic.getUuid());

        mBluetoothGatt.writeCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        Log.w(TAG,characteristic.getUuid()+" - Notify:"+enabled);
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to Heart Rate Measurement.
        if (SampleGattAttributes.UUID_GANGLION_RECEIVE.equals(characteristic.getUuid().toString())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
}
