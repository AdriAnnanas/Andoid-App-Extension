# GanglionAndroidBluetooth

This project is a extension of https://github.com/abek42/OpenBCIBLE and thus a further extension of
https://github.com/googlesamples/android-BluetoothLeGatt configured for the OpenBCI Ganglion board (http://openbci.com/).

The additions are mostly parts of the existing Python code https://github.com/OpenBCI/OpenBCI_Python, which have been translated to java code.

The current features are connecting to a Ganglion Board, converting the data to int and then either loggin the data or saving it to a .csv file along with the sampleID.

The data is also not yet scaled using the Scale factor to convert from counts to Volts: 

Scale Factor (Volts/count) = 1.2 Volts * 8388607.0 * 1.5 * 51.0 
from http://docs.openbci.com/Hardware/08-Ganglion_Data_Format#ganglion-data-format-interpreting-the-eeg-data

I tested the app with 2 devices, Android 7.0 and 9.0, please be aware that the minimum Android version is 5.0 (API lvl 21).


The packet loss has now been fixed and it should not drop more than 0.05% give or take.
This was acheived with  
gatt.requestConnectionPriority(CONNECTION_PRIORITY_HIGH);
suggested by NaughtiusMaximus here http://openbci.com/forum/index.php?p=/discussion/1786/ganglion-simblee-compatability-with-android-studio-project#latest

The code contains the functionality to receive 18Bit (with accelerometer) and 19Bit data as will as impedance values.

If you have any questions or suggestions please send me a mail to florian@mymind.life.

