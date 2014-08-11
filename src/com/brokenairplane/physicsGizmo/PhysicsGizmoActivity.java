/** Physics Gizmo by Phil Wagner
 * A free Android app so everyone can do science and analyze data.
 * www.brokenairplane.com
 * This code can be reused with attribution.
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

package com.brokenairplane.physicsGizmo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

public class PhysicsGizmoActivity extends Activity implements OnClickListener,
  TextWatcher, SensorEventListener {
  
  // Buttons
  protected Button startStop;
  protected ImageButton subtractTime;
  protected ImageButton howtoUse;
  protected ImageButton addTime;
  protected ImageButton udp;
  
  // Views
  protected TextView btLabel;
  protected TextView btUnits;
  protected TextView contextualHelp;
  protected TextView proximityOccurance1;
  protected TextView proximityOccurance2;
  protected TextView proximityOccurance3;
  protected TextView proximityTime1;
  protected TextView proximityTime2;
  protected TextView proximityTime3;
  protected TextView proximityTimeOnly;
  protected TextView sensingTime;
  protected TextView xAccelUnits;
  protected TextView yAccelUnits;
  protected TextView zAccelUnits;
  protected TextView xValue;
  protected TextView yValue;
  protected TextView zValue;
  protected EditText dataName;
 
  // State variables
  protected boolean btOn = false;
  protected boolean disabledStartButton = false;
  protected boolean isSensing = false;
  protected boolean readytoSend = false;
  protected boolean fromBT = false;
  protected boolean fromSensor = false;
  protected boolean newPhotoData = false;
  
  protected String currentName;
  protected String photoTime = "0";
  
  // Used to determine which phone initiated the BT sensing.
  public Long timeSensorSwitched;
  
  protected CountDownTimer senseCountDownTimer;
  public Spinner sensorSpinner;
  protected SensorManager sensorManager;
  
  // Sensor info
  protected float x = 0;
  protected float y = 0;
  protected float z = 0;
    
  protected int senseTime = 10;
  protected int proximityOccurance = 0;
  protected int eventCount = 0;
  
  //TODO localize if possible.
  protected int photoTemp1 = 0;
  protected int photoTemp2 = 0;
  protected int photoBT = 0;
  protected int photoSensor = 0;
  protected int timeElapsed = 0;
  
  protected static File csvFile;
  protected ViewFlipper MyViewFlipper;

  // Debugging
  private final String TAG = "PhysicsGizmoActivity";
  private final boolean D = true;
  
  // Sensor Types
  protected enum sensorTypes {
    ACCEL,
    PHOTO_ONE,
    PHOTO_TWO,
    PHOTO_PENDULUM
  }
  
  // Initialize
  protected sensorTypes currentSensor = sensorTypes.ACCEL;

  // Message types sent from the BluetoothChatService Handler
  public final static int MESSAGE_STATE_CHANGE = 1;
  public final static int MESSAGE_READ = 2;
  public final static int MESSAGE_WRITE = 3;
  public final static int MESSAGE_DEVICE_NAME = 4;
  public final static int MESSAGE_TOAST = 5;
  public final static int START_TIMER = 6;

  // Key names received from the BluetoothChatService Handler
  public final static String DEVICE_NAME = "device_name";
  public final static String TOAST = "toast";

  // Intent request codes
  private final int REQUEST_CONNECT_DEVICE = 1;
  private final int REQUEST_ENABLE_BT = 2;
  private final int REQUEST_DISCOVERABLE = 3;

  private TextView mTitle; // Shows in the UI who the phone is connected to

  // Name of the connected device
  private String mConnectedDeviceName = null;
  // String buffer for outgoing messages
  private StringBuffer mOutStringBuffer;
  // Local Bluetooth adapter
  private BluetoothAdapter mBluetoothAdapter = null;
  // Member object for the chat services
  private PhysicsGizmoBluetoothService mBluetoothService = null;
  
 
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (D) {
      Log.e(TAG, "+++ ON CREATE +++");
    }
		
		setContentView(R.layout.main);
		connectXMLViewtoJava();
		addUIClickListeners();
		addSensorListeners();
		setupDate(); //Get date for the title of the csv file
		setupSensorSpinner();
		setUnitFormatting();
		
		sensorSpinner.setSelection(0, false);  // Default is accelerometer.
		
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}


  @Override
  public void onStart() {
    super.onStart();
    if (D) {
      Log.e(TAG, "+++ ON START +++");
    }
  }


  @Override
  protected void onPause() {
    super.onPause();
    if (D) {
      Log.e(TAG, "+++ ON PAUSE +++");
    }
    // Cancel photogate if screen turns off.
    if ((isSensing) && (currentSensor != sensorTypes.ACCEL)) {
      senseCountDownTimer.cancel();
      senseCountDownTimer.onFinish();
    }
    resetSensorOccuranceVariables();

  }


  @Override
  protected void onResume() {
    super.onResume();
    if (D) {
      Log.e(TAG, "+++ ON RESUME +++");
    }

    /**
     * Performing this check in onResume() covers the case in which BT was not
     * enabled during onStart(), so we were paused to enable it.
     * onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
     * Only if state == STATE_NONE, do we know that we haven't started already.
     */
    if (mBluetoothService != null) {
      if (mBluetoothService.getState() ==
          PhysicsGizmoBluetoothService.STATE_NONE) {
        mBluetoothService.start();
      }
    }
  }

  
  @Override
  public void onStop() {
    super.onStop();
    if (D) {
      Log.e(TAG, "+++ ON STOP +++");
    }
  }

  
  @Override
  public void onDestroy() {
    if (D) {
      Log.e(TAG, "+++ ON DESTROY +++");
    }
    super.onDestroy();
    btOn = false;
    fromBT = false;
    fromSensor = false;
    photoBT = 0;
    photoSensor = 0;
    resetSensorOccuranceVariables();
    senseTime = 10;
    timeElapsed = 0;
    // Stop the Bluetooth chat services
    if (mBluetoothService != null) {
      mBluetoothService.stop();
    }
  }

  
  private void connectXMLViewtoJava(){
    /**
     * Connect XML Layout to UI
     */
    startStop = (Button) findViewById(R.id.start_stop);
    howtoUse = (ImageButton) findViewById(R.id.how_to_use);
    udp = (ImageButton) findViewById(R.id.udp_button);
    dataName = (EditText) findViewById(R.id.data_name);
    addTime = (ImageButton) findViewById(R.id.add_time);
    subtractTime = (ImageButton) findViewById(R.id.subtract_time);
    sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
    sensorSpinner = (Spinner) findViewById(R.id.sensor_spinner);
    btLabel = (TextView) findViewById(R.id.bt_label);
    btUnits = (TextView) findViewById(R.id.bt_units);
    sensingTime = (TextView) findViewById(R.id.sensing_time);
    contextualHelp = (TextView) findViewById(R.id.contextual_help);
    proximityOccurance1 = (TextView) findViewById(R.id.prox_occurance1);
    proximityOccurance2 = (TextView) findViewById(R.id.prox_occurance2);
    proximityOccurance3 = (TextView) findViewById(R.id.prox_occurance3);
    proximityTime1 = (TextView) findViewById(R.id.prox_time1);
    proximityTime2 = (TextView) findViewById(R.id.prox_time2);
    proximityTime3 = (TextView) findViewById(R.id.prox_time3);
    proximityTimeOnly = (TextView) findViewById(R.id.prox_time_only);
    xAccelUnits = (TextView) findViewById(R.id.xAccelUnits);
    yAccelUnits = (TextView) findViewById(R.id.yAccelUnits);
    zAccelUnits = (TextView) findViewById(R.id.zAccelUnits);
    xValue = (TextView) findViewById(R.id.x_value);
    yValue = (TextView) findViewById(R.id.y_value);
    zValue = (TextView) findViewById(R.id.z_value);
    MyViewFlipper = (ViewFlipper) findViewById(R.id.viewflipper);
    mTitle = (TextView) findViewById(R.id.mytitle);
    mTitle.setText(R.string.title_not_paired);
  }
  
  
  private void addUIClickListeners() {
    addTime.setOnClickListener(this);
    dataName.addTextChangedListener(this);
    howtoUse.setOnClickListener(this);
    udp.setOnClickListener(this);
    subtractTime.setOnClickListener(this);
    startStop.setOnClickListener(this);
  }
  
  
  private void addSensorListeners() {
    sensorManager.registerListener(this,
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
        SensorManager.SENSOR_DELAY_GAME);
    sensorManager.registerListener(this,
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
        SensorManager.SENSOR_DELAY_GAME);
  }
  
  
  private void setUnitFormatting() {
    /**
     * Formatting for acceleration units.
     */
    xAccelUnits.setText(Html
        .fromHtml("meters/sec<sup><small>2</small></sup"));
    yAccelUnits.setText(Html
        .fromHtml("meters/sec<sup><small>2</small></sup"));
    zAccelUnits.setText(Html
        .fromHtml("meters/sec<sup><small>2</small></sup"));    
  }
 

  private void resetSensorOccuranceVariables() {
    // TODO include all reset variables 
    proximityOccurance = 0;
    eventCount = 0;
    photoTemp1 = 0;
    photoTemp2 = 0;
  }

  
  private void setupSensorSpinner() {
    /** 
     * Spinner for the user to select a sensor to use.
     */
 
    // Sensing mode names
    final String MODE_NAME_ACCEL =
        getString(R.string.sensor_mode_name_accel);
    final String MODE_NAME_PHOTO_PENDULUM =
        getString(R.string.sensor_mode_name_photo_pendulum);
    final String MODE_NAME_PHOTO_ONE_PHONE =
        getString(R.string.sensor_mode_name_photo_one_phone);
    final String MODE_NAME_PHOTO_TWO_PHONES =
        getString(R.string.sensor_mode_name_photo_two_phones);
    
    final ArrayAdapter<CharSequence> sensorAdapter =
        new ArrayAdapter<CharSequence>(
        this, android.R.layout.simple_spinner_item);
    sensorAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
    if (sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).size() > 0) {
      sensorAdapter.add(MODE_NAME_ACCEL);
    }
    if (sensorManager.getSensorList(Sensor.TYPE_PROXIMITY).size() > 0) {
      sensorAdapter.add(MODE_NAME_PHOTO_PENDULUM);
      sensorAdapter.add(MODE_NAME_PHOTO_ONE_PHONE);
      // Android 2.0+ needed for Bluetooth.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
        sensorAdapter.add(MODE_NAME_PHOTO_TWO_PHONES);
      }
    }

    sensorSpinner.setAdapter(sensorAdapter);
    sensorSpinner.setOnItemSelectedListener(
      new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View v,
          int position, long id) {
        final String selectedSensor = sensorSpinner.getAdapter()
            .getItem(position).toString();
        if (MODE_NAME_ACCEL.equals(selectedSensor)) {
          disabledStartButton = false;
          startStop.setEnabled(true);
          MyViewFlipper.setDisplayedChild(0);
          contextualHelp.setText(R.string.accel_help);
          currentSensor = sensorTypes.ACCEL;
          resetForSensing();
        } else if (MODE_NAME_PHOTO_PENDULUM.equals(selectedSensor)) {
          // Photogate view
          MyViewFlipper.setDisplayedChild(1);
          currentSensor = sensorTypes.PHOTO_PENDULUM;
          contextualHelp.setText(R.string.pendulum_help);
          resetForSensing();
        } else if (MODE_NAME_PHOTO_ONE_PHONE.equals(selectedSensor)) {
          // Photogate with 1 phone
          disabledStartButton = false;
          startStop.setEnabled(true);
          // Photogate view
          MyViewFlipper.setDisplayedChild(1);
          contextualHelp.setText(R.string.gate1_help);
          currentSensor = sensorTypes.PHOTO_ONE;
          resetForSensing();
        } else if (MODE_NAME_PHOTO_TWO_PHONES.equals(selectedSensor)) {
          MyViewFlipper.setDisplayedChild(2);
          currentSensor = sensorTypes.PHOTO_TWO;
          if (mBluetoothAdapter != null) {
            requestBluetoothEnabled();
          }
        }
      }


      @Override
      public void onNothingSelected(AdapterView<?> parent) { 
      }    
    });
  }
 
  
  private void prepareEachPhoneForBluetoothPhotogate(boolean isFirst) {
    /**
     * For 2 phone photogate sensing.
     * The primary phone starts the sensing, the other phone is the
     * receiving phone (stops the sensing).
     */
    if (isFirst) {
      contextualHelp.setText(R.string.gate2_start_help);
      // TODO remove disabledStartButton variable and use isEnabled instead.
      disabledStartButton = false;
      startStop.setText(R.string.start_sensing);
      startStop.setEnabled(true);
      if (D) {
        Log.d(TAG, "first");
      }
    } else {
      if (D) {
        Log.d(TAG, "second");
      }
      contextualHelp.setText(R.string.gate2_stop_help);
      disabledStartButton = true;
      startStop.setText(R.string.button_disabled_message);
      startStop.setEnabled(false);
      addTime.setEnabled(false);
      subtractTime.setEnabled(false);
      dataName.setEnabled(false);
    }
    resetForSensing();
  }
 
  
  private void requestBluetoothEnabled() {
    if (!mBluetoothAdapter.isEnabled()) {
      Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
      // Otherwise, setup the connection between the two phones
    } else {
      if (mBluetoothService == null) {
        // TODO untangle this knot since both cases call this function.
        setupConnectedSensing();
      }
    }
  }
  
  
  private void setupConnectedSensing() {
    // Initialize the BluetoothService to perform Bluetooth connections
    mBluetoothService = new PhysicsGizmoBluetoothService(this, mHandler);

    // Initialize the buffer for outgoing messages
    mOutStringBuffer = new StringBuffer("");
    ensureDiscoverable();
  }
  
  
  private void ensureDiscoverable() {
    /**
     * Prompt user to make phone discoverable over Bluetooth.
     */
    if (D) {
      Log.d(TAG, "ensure discoverable");
    }
    if (mBluetoothAdapter.getScanMode() !=
        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
      Intent discoverableIntent = new Intent(
          BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
      discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
          300);
      startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);
    } else if (mBluetoothAdapter.getScanMode() ==
        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
      Intent serverIntent = new Intent(this, DeviceListActivity.class);
      startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
    }
  }

  
  private void sendMessage(String message) {
    /**
     * Sends a message. @param message A string of text to send.
     */
    if (mBluetoothService.getState() !=
        PhysicsGizmoBluetoothService.STATE_CONNECTED) {
      Toast.makeText(this, R.string.not_connected_verbose,
          Toast.LENGTH_SHORT).show();
      return;
    }
    // Check that there's actually something to send
    if (message.length() > 0) {
      // Get the message bytes and tell the BTService to write
      byte[] send = message.getBytes();
      mBluetoothService.write(send);

      // Reset out string buffer to zero and clear the edit text field
      mOutStringBuffer.setLength(0);
    }
  }

  
  private void sendInitialTimeMessage(String timeMessage){
    sendMessage(timeMessage);
  }
  
  
  private Handler mHandler = new Handler() {
    /**
     * The Handler receiving data from the PhysicsGizmoBluetoothService.
     */
    @Override
    public void handleMessage(Message msg) {
      switch (msg.what) {
      case MESSAGE_STATE_CHANGE:
        if (D) {
          Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
        }
        switch (msg.arg1) {
        case PhysicsGizmoBluetoothService.STATE_CONNECTED:
          mTitle.setText(R.string.title_connected_to_label);
          mTitle.append(mConnectedDeviceName);
          setSenseTime(10);
          btOn = true;
          //MyViewFlipper.setDisplayedChild(2);
          currentSensor = sensorTypes.PHOTO_TWO;
          String initialTimeMessage = getString(R.string.received_time_msg)
              .concat(String.valueOf(timeSensorSwitched)); 
          sendInitialTimeMessage(initialTimeMessage);
          break;
        case PhysicsGizmoBluetoothService.STATE_CONNECTING:
          mTitle.setText(R.string.title_connecting);
          break;
        case PhysicsGizmoBluetoothService.STATE_LISTEN:
        case PhysicsGizmoBluetoothService.STATE_NONE:
          mTitle.setText(R.string.title_not_paired);
          break;
        }
        break;
      case MESSAGE_WRITE:
        break;
      case MESSAGE_READ:
        byte[] readBuf = (byte[]) msg.obj;
        // construct a string from the valid bytes in the buffer
        String readMessage = new String(readBuf, 0, msg.arg1);
        if (D){
          Log.d(TAG,readMessage);
        }
        if (readMessage.equals(getString(R.string.start_timer_msg))) {
          currentName = createAllowedFilename(currentName);
          generateCsvFile(currentName + "." + getString(R.string.filetype_csv));
          startSensing();
        } else if (readMessage.equals(R.string.stop_timer_msg)) {
          stopSensing();
        } else if (readMessage.equals(getString(R.string.add_timer_msg))) {
          if (readytoSend) {
            if (disabledStartButton == true) {
              // If phone stopped before, when time
              // is added, keep it as the stopping phone.
              resetForSensing();
              contextualHelp.setText(R.string.gate2_stop_help);
              startStop.setEnabled(false);
              startStop.setText(getString(R.string.button_disabled_message));
            } else if (disabledStartButton == false) {
              // If phone started before, when time
              // is added, keep it as the starting phone.
              resetForSensing();
              contextualHelp.setText(R.string.gate2_start_help);
            }
          } else {
            setSenseTime(senseTime + 10);
          }
        } else if (readMessage.equals(getString(R.string.subtract_timer_msg))) {
          setSenseTime(senseTime - 10);
        } else if (readMessage.equals(getString(R.string.pulse_msg))) {
          // Photogate data sent
          if (fromBT == false) {
            // If BT data not collected, get it
            photoBT = timeElapsed;
            fromBT = true;
            if (fromSensor == true) {
              // If sensor data collected as well as BT data,
              // display it on the phone.
              fromBT = false;
              fromSensor = false;
              eventCount = 1;
              photoTime = String.valueOf(Math.abs(photoBT - photoSensor));
              newPhotoData = true;
              displayEventOverBT();
              startStop.performClick();
            }
          }
        } else if (readMessage.startsWith(getString(
            R.string.received_time_msg))) {
          // TODO replace all hardcoded message types.
          // See which phone connected first based on the fileName timestamp.
          Long initializedTime =
              Long.parseLong(readMessage.substring(getString(
                  R.string.received_time_msg).length()));
          if (timeSensorSwitched > initializedTime) {
            prepareEachPhoneForBluetoothPhotogate(true);
          } else {
            prepareEachPhoneForBluetoothPhotogate(false);
          }
        }
        break;
      case MESSAGE_DEVICE_NAME:
        // save the connected device's name
        mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
        Toast.makeText(getApplicationContext(),
            "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
        break;
      case MESSAGE_TOAST:
        Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
            Toast.LENGTH_SHORT).show();
        break;
      }
    }

  };

  
  private String createAllowedFilename(String fileName) {
    /**
     * Sanitize string for a filename.
     * -Change time colons to periods
     * -Remove characters not allowed in file names
     */
    fileName = fileName.replaceAll("[\":?/\\<>*|]", "_");
    Log.d(TAG, fileName);
    return fileName;
  }
  
  
  public void displayEventOverBT() {
    proximityTimeOnly.setText(photoTime);
  }

  
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (D) {
      Log.d(TAG, "onActivityResult " + resultCode);
    }
    switch (requestCode) {
    case REQUEST_CONNECT_DEVICE:
      // When DeviceListActivity returns with a device to connect
      if (resultCode == Activity.RESULT_OK) {
        // Get the device MAC address
        String address = data.getExtras().getString(
            DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BLuetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mBluetoothService.connect(device);
      }
      break;
    case REQUEST_ENABLE_BT:
      // When the request to enable Bluetooth returns
      if (resultCode == Activity.RESULT_OK) {
        // Bluetooth is now enabled, so set up a chat session
        setupConnectedSensing();
      } else {
        // User did not enable Bluetooth or an error occured
        Toast.makeText(this, R.string.bt_not_enabled_leaving,
            Toast.LENGTH_SHORT).show();
      }
      break;
    case REQUEST_DISCOVERABLE:
      // Phone is discoverable and ready to connect
      if (resultCode > 0) {
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
      } else {
        contextualHelp.setText(R.string.gate2_not_connected_help);
      }
      break;
    }
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    /**
     *  About Menu.
     */
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu, menu);
    return true;
  }

  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.about:
      AlertDialog.Builder aboutApp = new AlertDialog.Builder(this);
      aboutApp.setMessage(R.string.about_app);
      aboutApp.setPositiveButton(R.string.ok,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
            }
          });
      aboutApp.setNegativeButton(R.string.go_to_blog,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
              Intent browse = new Intent(Intent.ACTION_VIEW,
                Uri.parse(getString(R.string.blog_url)));
              startActivity(browse);
            }
          });
      aboutApp.show();
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }

  
  public void resetForSensing() {
    /**
     * Setup to sense again
     */
    setupDate();
    photoTime = "0";
    if (currentSensor != sensorTypes.PHOTO_TWO) {
      startStop.setText(R.string.start_sensing);
      addTime.setEnabled(true);
      subtractTime.setEnabled(true);
      dataName.setEnabled(true);
    }
    isSensing = false;
    readytoSend = false;
    fromBT = false;
    fromSensor = false;
    newPhotoData = false;
    senseTime = 10;
    setSenseTime(senseTime);
    sensingTime.setText(String.valueOf(senseTime) + " sec");
    proximityTimeOnly.setText("");
    photoBT = 0;
    photoSensor = 0;
    timeElapsed = 0;
    resetSensorOccuranceVariables();
  }

  
  @Override
  public void onClick(View v) {
    if (v == addTime) {
      if (isSensing) {
        return;
      } else if (readytoSend && currentSensor == sensorTypes.PHOTO_TWO) {
        sendMessage(getString(R.string.add_timer_msg));
        if (disabledStartButton == true) {
          /**
           * If this was the stopping phone before,
           * make it the stopping phone again.
           */
          resetForSensing();
          contextualHelp.setText(R.string.gate2_stop_help);
          startStop.setEnabled(false);
          startStop.setText(getString(R.string.button_disabled_message));
        } else if (disabledStartButton == false) {
          /**
           * If this was the starting phone before, make it the stopping phone.
           */
          resetForSensing();
          contextualHelp.setText(R.string.gate2_start_help);
        }
      } else if (readytoSend && currentSensor != sensorTypes.PHOTO_TWO) {
        // Reset the contextual help on added time
        resetForSensing();
        if (currentSensor == sensorTypes.ACCEL) {
          contextualHelp.setText(R.string.accel_help);
        } else if (currentSensor == sensorTypes.PHOTO_ONE) {
          contextualHelp.setText(R.string.gate1_help);
        } else if (currentSensor == sensorTypes.PHOTO_PENDULUM) {
          contextualHelp.setText(R.string.pendulum_help);
        }
      } else { // Normal add time
        setSenseTime(senseTime + 10);
        if (btOn == true) {
          sendMessage(getString(R.string.add_timer_msg));
        }
      }
    } else if (v == subtractTime) {
      if (isSensing) {
        return;
      } else {
        setSenseTime(senseTime - 10);
      }
      if (btOn == true) {
        sendMessage(getString(R.string.subtract_timer_msg));
      }
    } else if (v == startStop) {
      if (isSensing) {
        senseCountDownTimer.cancel();
        senseCountDownTimer.onFinish();
        if (btOn == true) {
          sendMessage(getString(R.string.stop_timer_msg));
        }
      } else if (readytoSend) {
        email(this);
      } else {
        currentName = createAllowedFilename(currentName);
        generateCsvFile(currentName + "." + getString(R.string.filetype_csv));
        startSensing();
        if (btOn == true) {
          sendMessage(getString(R.string.start_timer_msg));
        }
      }
    } else if (v == howtoUse) { // Help button instructions
      final int whichInstructions;
      switch (currentSensor) {
      case ACCEL:
        whichInstructions = R.string.instructions_accel;
        break;
      case PHOTO_ONE:
        whichInstructions = R.string.instructions_photo1;
        break;
      case PHOTO_TWO:
        whichInstructions = R.string.instructions_photo2;
        break;
      case PHOTO_PENDULUM:
        whichInstructions = R.string.instructions_pendulum;
        break;
       default:
         whichInstructions = R.string.instructions_accel;
      }
      AlertDialog.Builder instructions = new AlertDialog.Builder(this);
      instructions.setMessage(whichInstructions);
      instructions.setPositiveButton("OK",
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
            }
          });
      instructions.show();
    } else if (v == udp) {
      runUdpServer();
    }
  }

  
  private void runUdpServer() {
    final int UDP_SERVER_PORT = 7166;
    
    String udpMsg = "I am here" + UDP_SERVER_PORT;
    DatagramSocket ds = null;
    try {
        ds = new DatagramSocket();
        InetAddress serverAddr = InetAddress.getByName("192.168.1.78");
        DatagramPacket dp;
        dp = new DatagramPacket(udpMsg.getBytes(), udpMsg.length(), serverAddr, UDP_SERVER_PORT);
        ds.send(dp);
    } catch (SocketException e) {
        e.printStackTrace();
    }catch (UnknownHostException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    } catch (Exception e) {
        e.printStackTrace();
    } finally {
        if (ds != null) {
            ds.close();
        }
    }
  }
  
  


  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    // If back button is pressed while BT is synced,
    // warn user with toast before backing out of app.
    if (keyCode == KeyEvent.KEYCODE_BACK) { // Handle the back button
      if ((btOn == true)
          && (mBluetoothService.getState() ==
              PhysicsGizmoBluetoothService.STATE_CONNECTED)) {
        // Ask the user if they want to quit
        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.quit)
            .setMessage(R.string.bt_sensing_interrupt)
            .setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {

                  @Override
                  public void onClick(DialogInterface dialog, int which) {

                    // Stop the activity
                    PhysicsGizmoActivity.this.finish();
                  }

                }).setNegativeButton(R.string.cancel, null).show();

        return true;
      } else {
        return super.onKeyDown(keyCode, event);
      }
    }
    return false;
  }
  
  
  private void addtoCSV(File csvFile, String[] rowData){
    try {
      FileWriter writer = new FileWriter(csvFile, true);
      final int len = rowData.length;
      for (int col = 0; col < len; col++) {
        writer.append(rowData[col]);
        if (col < len - 1) {
          writer.append(","); 
        }
      }
      writer.append("\n");
      writer.flush();
      writer.close();
    } catch (IOException e) {
        e.printStackTrace();
      }
  }

  
  private void generateCsvFile(String fileName) {
    /**
     * Necessary to get the sdcard directory as it may difer among phones.
     * TODO replace with approved Android method.
     */
    File sdCard = Environment.getExternalStorageDirectory();
    File dir = new File(sdCard.getAbsolutePath() + "/ScienceData");
    // Save in ScienceData folder on SD card
    dir.mkdirs();
    csvFile = new File(dir, fileName);
    if (currentSensor == sensorTypes.ACCEL) {
      final String[] accelColHeaders = {"time (ms)", "x (m/m^2)",
                                        "y (m/m^2)", "z (m/m^2)"};
      addtoCSV(csvFile, accelColHeaders);
    }
    if (currentSensor != sensorTypes.ACCEL) {
      /**
       * Could replace with else but leaving it specific in case of future
       * sensors which are not photogates.
       */
      // Photogate column headers
      final String[] photoColHeaders = {"Event", "time (ms)"};
      addtoCSV(csvFile, photoColHeaders);
    }
  }
  

  public void email(Context context) {
    final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
    emailIntent.setType("text/csv"); // MIME Type
    emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
        getString(R.string.email_subject));
    emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
        getString(R.string.email_body1) + createAllowedFilename(currentName) +
        getString(R.string.email_body2));
    emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file://" + csvFile));
    context.startActivity(Intent.createChooser(emailIntent,
        getString(R.string.send_email)));
  }

  
  public void startSensing() {
    final long one_second = 1000;
    final long dt = 10;
    final Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    v.vibrate(one_second / 2);
    senseCountDownTimer = new CountDownTimer(senseTime * one_second, 100) {
      @Override
      public void onTick(long millisUntilFinished) {
        if (isSensing == true) {
          sensingTime.setText(String.valueOf(millisUntilFinished / one_second)
              + getString(R.string.timer_units));
        }
        timeElapsed += dt;
        if (currentSensor == sensorTypes.ACCEL) {
          /**
           * TODO remove the duplication by looping through a data structure of
           * all of the data to append.
           */
          // Record the time in the csv for graphing purposes
          final String[] accelData = {String.valueOf(timeElapsed),
                                      String.valueOf(x),
                                      String.valueOf(y),
                                      String.valueOf(z)};
           addtoCSV(csvFile, accelData);
        } else if (currentSensor != sensorTypes.ACCEL) {
          if (newPhotoData == true) {
            final String[] photoData = {String.valueOf(eventCount), photoTime};
            addtoCSV(csvFile, photoData);
            newPhotoData = false;
          }
        }
      }

      @Override
      public void onFinish() {
        isSensing = false;
        v.vibrate(one_second / 2);
        sensingTime.setText(getString(R.string.timer_finished));
        if (startStop.isEnabled()) { 
          startStop.setText(getText(R.string.email_upload));
        }
        readytoSend = true;
        contextualHelp.setText(R.string.stopped_help);
      }
    };
    senseCountDownTimer.start();
    if (!disabledStartButton) {
      startStop.setText(getString(R.string.stop_sensing));
    }
    isSensing = true;
  }

  
  public void stopSensing() {
    senseCountDownTimer.cancel();
    senseCountDownTimer.onFinish();
  }

  
  public void setupDate() {
    // Time for file name
    Date cal = Calendar.getInstance().getTime(); // Get current time
    Calendar cal2 = Calendar.getInstance();
    timeSensorSwitched = cal2.getTimeInMillis();
    currentName = cal.toLocaleString();
    dataName.setText(currentName);
  }

  // Change the time to sense
  public void setSenseTime(int seconds) {
    final int minTime = 10;
    final int maxTime = 300;
    if (seconds < minTime) {
      senseTime = minTime;
    } else if (seconds > maxTime) {
      senseTime = maxTime;
    } else {
      senseTime = seconds;
    }
    sensingTime.setText(String.valueOf(senseTime) + " sec");
  }

  
  @Override
  public void afterTextChanged(Editable s) {
    // If the name of the data changed then change the file name.
    currentName = createAllowedFilename(s.toString());
  }

  
  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    // Not using this
  }

  
  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    // Not using this
  }

  
  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
    // Not using this
  }

  
  @Override
  public void onSensorChanged(SensorEvent event) {
    switch (currentSensor) {
    case ACCEL:
      if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        x = event.values[0];
        y = event.values[1];
        z = event.values[2];
        
        xValue.setText(String.valueOf(Math.round(x * 10) / 10.0));
        yValue.setText(String.valueOf(Math.round(y * 10) / 10.0));
        zValue.setText(String.valueOf(Math.round(z * 10) / 10.0));
      }
      break;
    case PHOTO_ONE:
      if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
        if (event.values[0] > 0) {
          if (isSensing) {
            eventCount += 1;
            // Count time from sensor covered to sensor uncovered
            photoTime = String.valueOf(timeElapsed);
            newPhotoData = true;
            updateEventDisplay(eventCount);
          }
        }
      }
      break;
    case PHOTO_TWO:
      if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
        if (event.values[0] > 0) {
          if (isSensing) {
            if (eventCount == 0) {
              if (fromSensor == false) {
                photoSensor = timeElapsed;
                fromSensor = true;
                sendMessage(getString(R.string.pulse_msg));
                if (fromBT == true) {
                  /**
                   * Reset the variables, not needed right now but if later more
                   * events are added this will be necessary.
                   */
                  fromBT = false;
                  fromSensor = false;
                }
              }
            }

          }
        }
      }
      break;
    case PHOTO_PENDULUM:
      if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
        if (event.values[0] > 0) {
          if (isSensing) {
            proximityOccurance += 1;
            if (proximityOccurance % 2 == 1) {// Every odd swing
              photoTemp1 = timeElapsed;
            }
            if (proximityOccurance % 2 == 0) {
              // Allows data events to scroll up the page
              photoTemp2 = timeElapsed;
              eventCount += 1;
              photoTime = String.valueOf(photoTemp2 - photoTemp1);
              newPhotoData = true;
              updateEventDisplay(eventCount);
            }
          }
        }
      }
      break;
    }
  }

  
  private void updateEventDisplay(int eventCount) {
    if (eventCount > 3) {
      // Allows data events to scroll up the page.
      proximityOccurance1.setText(proximityOccurance2.getText());
      proximityTime1.setText(proximityTime2.getText());
      proximityOccurance2.setText(proximityOccurance3.getText());
      proximityTime2.setText(proximityTime3.getText());
      proximityOccurance3.setText(String.valueOf(eventCount));
      proximityTime3.setText(photoTime);
    } else if (eventCount == 1) {
        proximityOccurance1.setText(String.valueOf(eventCount));
        proximityTime1.setText(String.valueOf(photoTime));
    } else if (eventCount == 2) {
        proximityOccurance2.setText(String.valueOf(eventCount));
        proximityTime2.setText(String.valueOf(photoTime));
    } else if (eventCount == 3) {
        proximityOccurance3.setText(String.valueOf(eventCount));
        proximityTime3.setText(String.valueOf(photoTime));
    }
  }
}