/*Physics Gizmo by Phil Wagner
*A free Android app so everyone can do science and analyze data.
*www.brokenairplane.com
*This code can be reused with attribution.
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
	public Spinner sensorSpinner;
	protected Spinner timeSpinner;
	protected EditText dataName;
	protected String currentName;
	protected String photoTime = "0";
	protected ImageButton addTime;
	protected ImageButton subtractTime;
	protected ImageButton howtoUse;
	protected Button startStop;
	protected boolean btOn = false;
	protected boolean disabledStartButton = false; // On BT turns off the
													// receiving button
	protected boolean isSensing = false;
	protected boolean readytoSend = false;
	protected boolean fromBT = false;
	protected boolean fromSensor = false;
	protected boolean newPhotoData = false;
	protected CountDownTimer senseCountDownTimer;
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
	protected SensorManager sensorManager;
	protected float x = 0;
	protected float y = 0;
	protected float z = 0;
	protected int dt = 100;
	protected int tempPulseTime1 = 0;
	protected int tempPulseTime2 = 0;
	protected int senseTime = 10;
	protected int sensorType = 0;
	protected int proximityOccurance = 0;
	protected int eventCount = 0;
	protected int photoTemp1 = 0;
	protected int photoTemp2 = 0;
	protected int photoTemp3 = 0;
	protected int photoBT = 0;
	protected int photoSensor = 0;
	protected int timeElapsed = 0;
	protected int whichInstructions;
	protected String fileName;
	protected static File csvFile;
	protected Vibrator v;
	protected ViewFlipper MyViewFlipper;

	// ///////////////////////////////////////////////////////////////////////////////////////////
	// Debugging
	private final String TAG = "PhysicsGizmoActivity";
	private final boolean D = false;

	// Message types sent from the BluetoothChatService Handler
	public final static int MESSAGE_STATE_CHANGE = 1;
	public final static int MESSAGE_READ = 2;
	public final static int MESSAGE_WRITE = 3;
	public final static int MESSAGE_DEVICE_NAME = 4;
	public final static int MESSAGE_TOAST = 5;
	public final int START_TIMER = 6;

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

	// ///////////////////////////////////////////////////////////////////////////////////////////

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.main);

		// Connect XML to UI
		startStop = (Button) findViewById(R.id.start_stop);
		howtoUse = (ImageButton) findViewById(R.id.how_to_use);
		dataName = (EditText) findViewById(R.id.data_name);
		addTime = (ImageButton) findViewById(R.id.add_time);
		subtractTime = (ImageButton) findViewById(R.id.subtract_time);
		sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		sensorSpinner = (Spinner) findViewById(R.id.sensor_spinner);
		btLabel = (TextView) findViewById(R.id.bt_label);
		btUnits = (TextView) findViewById(R.id.bt_units);
		sensingTime = (TextView) findViewById(R.id.sensing_time);
		contextualHelp = (TextView) findViewById(R.id.contextual_help);
		proximityOccurance1 = (TextView) findViewById(R.id.proximity_occurance1);
		proximityOccurance2 = (TextView) findViewById(R.id.proximity_occurance2);
		proximityOccurance3 = (TextView) findViewById(R.id.proximity_occurance3);
		proximityTime1 = (TextView) findViewById(R.id.proximity_time1);
		proximityTime2 = (TextView) findViewById(R.id.proximity_time2);
		proximityTime3 = (TextView) findViewById(R.id.proximity_time3);
		proximityTimeOnly = (TextView) findViewById(R.id.proximity_time_only);
		xAccelUnits = (TextView) findViewById(R.id.xAccelUnits);
		yAccelUnits = (TextView) findViewById(R.id.yAccelUnits);
		zAccelUnits = (TextView) findViewById(R.id.zAccelUnits);
		xValue = (TextView) findViewById(R.id.x_value);
		yValue = (TextView) findViewById(R.id.y_value);
		zValue = (TextView) findViewById(R.id.z_value);
		v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		MyViewFlipper = (ViewFlipper) findViewById(R.id.viewflipper);
		mTitle = (TextView) findViewById(R.id.mytitle);
		mTitle.setText(R.string.app_name);

		// Click listeners
		addTime.setOnClickListener(this);
		dataName.addTextChangedListener(this);
		howtoUse.setOnClickListener(this);
		subtractTime.setOnClickListener(this);
		startStop.setOnClickListener(this);

		// Sensors
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
				SensorManager.SENSOR_DELAY_GAME);
		sensorManager.registerListener(this,
				sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY),
				SensorManager.SENSOR_DELAY_GAME);

		setupDate(); //Get date for the title of the csv file

		// Formatting for acceleration units
		xAccelUnits.setText(Html
				.fromHtml("meters/sec<sup><small>2</small></sup"));
		yAccelUnits.setText(Html
				.fromHtml("meters/sec<sup><small>2</small></sup"));
		zAccelUnits.setText(Html
				.fromHtml("meters/sec<sup><small>2</small></sup"));

		// Spinner for sensors, # of sensors determined by if Android version is higher than 2.0.
		final ArrayAdapter<CharSequence> sensorAdapter = new ArrayAdapter<CharSequence>(
				this, android.R.layout.simple_spinner_item);
		sensorAdapter
				.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		if (sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER).size() > 0) {
			sensorAdapter.add("Accelerometer");
		}
		if (sensorManager.getSensorList(Sensor.TYPE_PROXIMITY).size() > 0) {
			sensorAdapter.add("Photogate Pulse - 1 Phone");
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) { // Android 2.0+ needed for Bluetooth
				sensorAdapter.add("Photogate Pulse - 2 Phones");
			}
			sensorAdapter.add("Photogate Pendulum");
		}

		sensorSpinner.setAdapter(sensorAdapter);
		sensorSpinner.setSelection(0, true);
		sensorSpinner
				.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
					@Override
					public void onItemSelected(AdapterView<?> parent, View v,
							int position, long id) {
						if (position == 0) { // Accelerometer
							disabledStartButton = false;
							startStop.setEnabled(true);
							MyViewFlipper.setDisplayedChild(0);
							resetForSensing();
							contextualHelp.setText(R.string.accel_help);
							sensorType = 0;
						} else if (position == 1) { // Photogate with 1 phone
							disabledStartButton = false;
							startStop.setEnabled(true);
							MyViewFlipper.setDisplayedChild(1); // Photogate view
							resetForSensing();
							contextualHelp.setText(R.string.gate1_help);
							sensorType = 1;
						} else if (position == 2) {
							if (sensorAdapter.getCount() == 3) {
								disabledStartButton = false;
								startStop.setEnabled(true);
								MyViewFlipper.setDisplayedChild(1); // Photogate view
								sensorType = 3; // Pendulum
								resetForSensing();
								contextualHelp.setText(R.string.pendulum_help);
							} else {
								sensorType = 2; // Gate 2
								if (mTitle.getText().toString()
										.equals("Not Connected")) {
									ensureDiscoverable();
									MyViewFlipper.setDisplayedChild(2); // Photogate 2 view
									resetForSensing();
									contextualHelp
											.setText(R.string.gate2_start_help);
								} 
								else
								{
									MyViewFlipper.setDisplayedChild(2); // Photogate 2 view
									contextualHelp
											.setText(R.string.gate2_stop_help);
									disabledStartButton = true;
									startStop.setEnabled(false);
									startStop.setText("Check other phone");
								}
							}

						} else if (position == 3) {
							MyViewFlipper.setDisplayedChild(1); // Photogate
																// view
							sensorType = 3; // Pendulum
							contextualHelp.setText(R.string.pendulum_help);
						}
					}

					@Override
					public void onNothingSelected(AdapterView<?> parent) {
						// Nothing
					}
				});

		// /////////////////////////////////////////////////////////////////////////////////////////////////
		if (D) {
			Log.e(TAG, "+++ ON CREATE +++");
		}
		
		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	}

	// /////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	public void onStart() {
		super.onStart();
		if (D) {
			Log.e(TAG, "++ ON START ++");
		}
		// If BT not enabled, request it to be enabled.
		if (mBluetoothAdapter != null) {
			if (!mBluetoothAdapter.isEnabled()) {
				Intent enableIntent = new Intent(
						BluetoothAdapter.ACTION_REQUEST_ENABLE);
				startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
				// Otherwise, setup the connection between the two phones
			} else {
				if (mBluetoothService == null) {
					setupConnectedSensing();
				}
			}
		}
	}

	// /////////////////////////////////////////////////////////////////////////////////////////////////

	@Override
	protected void onPause() {
		super.onPause();
		if (D) {
			Log.e(TAG, "- ON PAUSE -");
		}
		// Cancel photogate if screen turns off.
		if ((isSensing) && (sensorType > 0)) {
			senseCountDownTimer.cancel();
			senseCountDownTimer.onFinish();
		}
		proximityOccurance = 0;
		eventCount = 0;
		photoTemp1 = 0;
		photoTemp2 = 0;
		photoTemp3 = 0;
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (D) {
			Log.e(TAG, "+ ON RESUME +");
		}

		// ////////////////////////////////////////////////////////////////////////////////////////////////
		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity
		// returns.
		if (mBluetoothService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// started already
			if (mBluetoothService.getState() == PhysicsGizmoBluetoothService.STATE_NONE) {
				// Start the Bluetooth chat services
				mBluetoothService.start();
			}
		}
		// ////////////////////////////////////////////////////////////////////////////////////////////////
	}

	@Override
	public void onStop() {
		super.onStop();
		if (D) {
			Log.e(TAG, "-- ON STOP --");
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		btOn = false;
		fromBT = false;
		fromSensor = false;
		photoBT = 0;
		photoSensor = 0;
		proximityOccurance = 0;
		eventCount = 0;
		photoTemp1 = 0;
		photoTemp2 = 0;
		photoTemp3 = 0;
		senseTime = 10;
		timeElapsed = 0;
		// Stop the Bluetooth chat services
		if (mBluetoothService != null) {
			mBluetoothService.stop();
		}
		if (D) {
			Log.e(TAG, "--- ON DESTROY ---");
		}
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////
	private void setupConnectedSensing() {

		// Initialize the BluetoothService to perform Bluetooth connections
		mBluetoothService = new PhysicsGizmoBluetoothService(this, mHandler);

		// Initialize the buffer for outgoing messages
		mOutStringBuffer = new StringBuffer("");
	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////

	// ////////////////////////////////////////////////////////////////////////////////////////////////////

	// Ask user to make phone discoverable
	private void ensureDiscoverable() {
		if (D) {
			Log.d(TAG, "ensure discoverable");
		}
		if (mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent discoverableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
			discoverableIntent.putExtra(
					BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
			startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE);
		} else if (mBluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
		}

	}

	// Sends a message. @param message A string of text to send.
	private void sendMessage(String message) {
		// Check that we're actually connected before trying anything
		if (mBluetoothService.getState() != PhysicsGizmoBluetoothService.STATE_CONNECTED) {
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
					.show();
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

	// The Handler that gets information back from the
	// PhysicsGizmoBluetoothService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if (D) {
					Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				}
				switch (msg.arg1) {
				case PhysicsGizmoBluetoothService.STATE_CONNECTED:
					mTitle.setText(R.string.title_connected_to);
					mTitle.append(mConnectedDeviceName);
					setSenseTime(10);
					sensorType = 2;
					sensorSpinner.setSelection(2, true);
					btOn = true;
					break;
				case PhysicsGizmoBluetoothService.STATE_CONNECTING:
					mTitle.setText(R.string.title_connecting);
					break;
				case PhysicsGizmoBluetoothService.STATE_LISTEN:
				case PhysicsGizmoBluetoothService.STATE_NONE:
					mTitle.setText(R.string.title_not_connected);
					break;
				}
				break;
			case MESSAGE_WRITE:
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				if (readMessage.equals("startTimer")) {
					currentName = currentName.replace(":", "."); // Change time colons to periods
					currentName = currentName.replace("?/\\<>*|", ""); // Not allowed characters
					generateCsvFile(currentName + ".csv");
					startSensing();
				} else if (readMessage.equals("stopTimer")) {
					stopSensing();
				} else if (readMessage.equals("addTimer")) {
					if (readytoSend == true) {
						if (disabledStartButton == true) { // If phone stopped before, when time
															// is added, keep it as the stopping phone.
							resetForSensing();
							contextualHelp.setText(R.string.gate2_stop_help);
							startStop.setEnabled(false);
							startStop.setText("Check other phone");
						} else if (disabledStartButton == false) { // If phone started before, when time
																	// is added, keep it as the starting phone.
							resetForSensing();
							contextualHelp.setText(R.string.gate2_start_help);
						}
					} else {
						setSenseTime(senseTime + 10);
					}
				} else if (readMessage.equals("subtractTimer")) {
					setSenseTime(senseTime - 10);
				} else if (readMessage.equals("pulse")) { // Photogate data sent
					if (fromBT == false) { // If BT data not collected, get it
						photoBT = timeElapsed;
						fromBT = true;
						if (fromSensor == true) { // If sensor data collected as
													// well as BT data, display
													// it on the phone
							fromBT = false;
							fromSensor = false;
							eventCount = 1;
							photoTime = String.valueOf(Math.abs(photoBT
									- photoSensor));
							newPhotoData = true;
							displayEventOverBT();
						}
					}
				}
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

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
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
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

	// ////////////////////////////////////////////////////////////////////////////////////////////////////

	// Menu about the app
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
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
			aboutApp.setPositiveButton("OK",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
						}
					});
			aboutApp.setNegativeButton("BrokenAirplane",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							Intent browse = new Intent(
									Intent.ACTION_VIEW,
									Uri.parse("http://www.brokenairplane.com/2011/10/android-education-science-physicsgizmo.html"));
							startActivity(browse);
						}
					});
			aboutApp.show();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// Gets app setup to sense again
	public void resetForSensing() {
		setupDate();
		photoTime = "0";
		startStop.setText(R.string.start_sensing);
		isSensing = false;
		readytoSend = false;
		fromBT = false;
		fromSensor = false;
		newPhotoData = false;
		tempPulseTime1 = 0;
		tempPulseTime2 = 0;
		senseTime = 10;
		setSenseTime(senseTime);
		sensingTime.setText(String.valueOf(senseTime) + " sec");
		proximityTimeOnly.setText("");
		proximityOccurance = 0;
		eventCount = 0;
		photoTemp1 = 0;
		photoTemp2 = 0;
		photoTemp3 = 0;
		photoBT = 0;
		photoSensor = 0;
		timeElapsed = 0;
	}

	@Override
	public void onClick(View v) {
		if (v == addTime) {
			if (isSensing) {
				return;
			} else if ((readytoSend == true) && (sensorType == 2)) { // Photogate pulse with two phones
				sendMessage("addTimer");
				if (disabledStartButton == true) { // If stopping phone before,
													// make it the stopping
													// phone again
					resetForSensing();
					contextualHelp.setText(R.string.gate2_stop_help);
					startStop.setEnabled(false);
					startStop.setText("Check other phone");
				} else if (disabledStartButton == false) { // If starting phone before, make it
															// the stopping phone
					resetForSensing();
					contextualHelp.setText(R.string.gate2_start_help);
				}
			} else if ((readytoSend == true) && (sensorType != 2)) { // Reset the contextual help
																	 // on added time
				resetForSensing();
				if (sensorType == 0) {
					contextualHelp.setText(R.string.accel_help);
				} else if (sensorType == 1) {
					contextualHelp.setText(R.string.gate1_help);
				} else if (sensorType == 3) {
					contextualHelp.setText(R.string.pendulum_help);
				}
			} else { // Normal add time
				setSenseTime(senseTime + 10);
				if (btOn == true) {
					sendMessage("addTimer");
				}
			}
		} else if (v == subtractTime) {
			if (isSensing) {
				return;
			} else {
				setSenseTime(senseTime - 10);
			}
			if (btOn == true) {
				sendMessage("subtractTimer");
			}
		} else if (v == startStop) {
			if (isSensing) {
				senseCountDownTimer.cancel();
				senseCountDownTimer.onFinish();
				if (btOn == true) {
					sendMessage("stopTimer");
				}
			} else if (readytoSend) {
				email(this);
			} else {
				currentName = currentName.replace(":", "."); // Not allowed characters
				currentName = currentName.replace("?/\\<>*|", "");
				generateCsvFile(currentName + ".csv");
				startSensing();
				if (btOn == true) {
					sendMessage("startTimer");
				}
			}
		} else if (v == howtoUse) { // Help button instructions
			switch (sensorType) {
			case 0:
				whichInstructions = R.string.instructions_accel;
				break;
			case 1:
				whichInstructions = R.string.instructions_photo1;
				break;
			case 2:
				whichInstructions = R.string.instructions_photo2;
				break;
			case 3:
				whichInstructions = R.string.instructions_pendulum;
				break;
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
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) { // If back button is
															// pressed while BT
															// is synced, warn
															// user with toast
															// before backing
															// out of app.
		// Handle the back button
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if ((btOn == true)
					&& (mBluetoothService.getState() == PhysicsGizmoBluetoothService.STATE_CONNECTED)) {
				// Ask the user if they want to quit
				new AlertDialog.Builder(this)
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setTitle(R.string.quit)
						.setMessage(R.string.bt_sensing_interrupt)
						.setPositiveButton(R.string.ok,
								new DialogInterface.OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {

										// Stop the activity
										PhysicsGizmoActivity.this.finish();
									}

								}).setNegativeButton(R.string.cancel, null)
						.show();

				return true;
			} else {
				return super.onKeyDown(keyCode, event);
			}
		}
		return false;
	}

	private void generateCsvFile(String fileName) {
		try {
			File sdCard = Environment.getExternalStorageDirectory(); // Necessary to get
																	 // the sdcard directory
																     // as it may be different
																	// for different phones
			File dir = new File(sdCard.getAbsolutePath() + "/ScienceData"); // Save in ScienceData
																			// folder on SD card
			dir.mkdirs();
			csvFile = new File(dir, fileName);
			FileWriter writer = new FileWriter(csvFile);
			if (sensorType == 0) {
				writer.append("time (ms)"); // Categories In 1.2 version the
											// data headers were made lowercase
											// and units were added.
				writer.append(","); // Comma Separated Values (csv)
				writer.append("x (m/m^2)");
				writer.append(",");
				writer.append("y (m/m^2)");
				writer.append(',');
				writer.append("z (m/m^2)");
				writer.append("\n");
				writer.flush();
				writer.close();
			}
			if (sensorType > 0) { // Photogate column headers
				writer.append("Event");
				writer.append(",");
				writer.append("time (ms)");
				writer.append("\n");
				writer.flush();
				writer.close();
			}
		} catch (IOException e) // If the name of the file is illegal then no
								// file is created
		{
			e.printStackTrace();
		}
	}

	public void email(Context context) {
		final Intent emailIntent = new Intent(
				android.content.Intent.ACTION_SEND);
		emailIntent.setType("text/csv"); // MIME Type
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
				"Science Data"); // Prefill information
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT,
				"Attached is the " + currentName + " data.");
		emailIntent.putExtra(Intent.EXTRA_STREAM,
				Uri.parse("file://" + csvFile));
		context.startActivity(Intent.createChooser(emailIntent, "Send mail..."));
	}

	public void startSensing() {
		v.vibrate(500);
		senseCountDownTimer = new CountDownTimer(senseTime * 1000, dt) {
			@Override
			public void onTick(long millisUntilFinished) {
				if (isSensing == true) {
					sensingTime.setText(String
							.valueOf(millisUntilFinished / 1000) + " sec");
				}
				timeElapsed += dt;  //Record the time in the csv for graphing
									// purposes
				if (sensorType == 0) {
					try {
						FileWriter writer = new FileWriter(csvFile, true);
						writer.append(String.valueOf(timeElapsed));
						writer.append(",");
						writer.append(String.valueOf(x));
						writer.append(",");
						writer.append(String.valueOf(y));
						writer.append(',');
						writer.append(String.valueOf(z));
						writer.append("\n");
						writer.flush();
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else if (sensorType > 0) {
					if (newPhotoData == true) {
						try {
							FileWriter writer = new FileWriter(csvFile, true);
							writer.append(String.valueOf(eventCount));
							writer.append(",");
							writer.append(photoTime);
							writer.append("\n");
							writer.flush();
							writer.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						newPhotoData = false;
					}
				}
			}

			@Override
			public void onFinish() {
				isSensing = false;
				v.vibrate(500);
				sensingTime.setText("Done");
				startStop.setText("Email/Upload");
				readytoSend = true;
				contextualHelp.setText(R.string.stopped_help);
			}
		};
		senseCountDownTimer.start();
		if (!disabledStartButton) {
			startStop.setText("Stop Sensing");
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
		currentName = cal.toLocaleString();
		dataName.setText(currentName);
	}

	// Change the time to sense
	public void setSenseTime(int seconds) {
		if (seconds < 10) {
			senseTime = 10;
		} else if (seconds > 300) {
			senseTime = 300;
		} else {
			senseTime = seconds;
		}
		sensingTime.setText(String.valueOf(senseTime) + " sec");
	}

	@Override
	public void afterTextChanged(Editable s) { // If the name of the data
												// changed then change the file name
		currentName = s.toString();
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
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
		switch (sensorType) {
		case 0: // Accelerometer
			if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

				x = event.values[0];
				y = event.values[1];
				z = event.values[2];

				xValue.setText(String.valueOf(Math.round(x * 10) / 10.0));
				yValue.setText(String.valueOf(Math.round(y * 10) / 10.0));
				zValue.setText(String.valueOf(Math.round(z * 10) / 10.0));
			}
			break;
		case 1: // Pulse 1 phone
			if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
				if (event.values[0] > 0) {
					if (isSensing) {
						eventCount += 1;
						tempPulseTime2 = timeElapsed;
						photoTime = String.valueOf(tempPulseTime2); // Count time from
																	// sensor covered
																	// to sensor uncovered
						newPhotoData = true;
						if (eventCount > 3) { // Allows data events to scroll up
												// the page
							proximityOccurance1.setText(proximityOccurance2
									.getText());
							proximityTime1.setText(proximityTime2.getText());
							proximityOccurance2.setText(proximityOccurance3
									.getText());
							proximityTime2.setText(proximityTime3.getText());
							proximityOccurance3.setText(String
									.valueOf(eventCount));
							proximityTime3.setText(photoTime);
						} else {
							switch (eventCount) {
							case 1:
								proximityOccurance1.setText(String
										.valueOf(eventCount));
								proximityTime1.setText(String
										.valueOf(photoTime));
								break;
							case 2:
								proximityOccurance2.setText(String
										.valueOf(eventCount));
								proximityTime2.setText(String
										.valueOf(photoTime));
								break;
							case 3:
								proximityOccurance3.setText(String
										.valueOf(eventCount));
								proximityTime3.setText(String
										.valueOf(photoTime));
								break;
							}
						}
					}
				}
			}
			break;
		case 2: // Pulse 2 Phone
			if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
				if (event.values[0] > 0) {
					if (isSensing) {
						if (eventCount == 0) {
							if (fromSensor == false) {
								photoSensor = timeElapsed;
								fromSensor = true;
								sendMessage("pulse");

								if (fromBT == true) { // Reset the variables,
														// not needed right now
														// but if later more
														// events are added this
														// will be necessary.
									fromBT = false;
									fromSensor = false;
								}
							}
						}

					}
				}
			}
			break;
		case 3: // Pendulum
			if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
				if (event.values[0] > 0) {
					if (isSensing) {
						proximityOccurance += 1;
						if (proximityOccurance % 2 == 1) {// Every odd swing
							photoTemp1 = timeElapsed;
						}
						if (proximityOccurance % 2 == 0) { // Allows data events
															// to scroll up the
															// page
							photoTemp2 = timeElapsed;
							eventCount += 1;
							photoTime = String.valueOf(photoTemp2 - photoTemp1);
							newPhotoData = true;
							if (eventCount > 3) { //
								proximityOccurance1.setText(proximityOccurance2
										.getText());
								proximityTime1
										.setText(proximityTime2.getText());
								proximityOccurance2.setText(proximityOccurance3
										.getText());
								proximityTime2
										.setText(proximityTime3.getText());
								proximityOccurance3.setText(String
										.valueOf(eventCount));
								proximityTime3.setText(String
										.valueOf(photoTime));
							} else {
								switch (eventCount) {
								case 1:
									proximityOccurance1.setText(String
											.valueOf(eventCount));
									proximityTime1.setText(String
											.valueOf(photoTime));
									break;
								case 2:
									proximityOccurance2.setText(String
											.valueOf(eventCount));
									proximityTime2.setText(String
											.valueOf(photoTime));
									break;
								case 3:
									proximityOccurance3.setText(String
											.valueOf(eventCount));
									proximityTime3.setText(String
											.valueOf(photoTime));
									break;
								}
							}
						}
					}
				}
			}
			break;
		}
	}
}