package kz.kazintech.barcodereader;

import android.bluetooth.BluetoothAdapter;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

public class MainActivity extends ActionBarActivity {

	private static final String TAG = "BluetoothChat";
	private static final boolean D = true;
    public static final String DEF_URL = "http://217.76.72.194/sap_new/barcode/index.php";
    public static final String DEF_DELAY = "10";

	// Intent request codes
	private static final int REQUEST_ENABLE_BT = 3;

	// Message types sent from the BluetoothChatService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;
    public static final int MESSAGE_LOG = 6;

	private static final int SETTINGS_RESULT = 1;
	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String DEVICE_ADDR = "device_addr";
	public static final String TOAST = "toast";
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	private BluetoothChatService mChatService = null;
    // Array adapter for the conversation thread
	private ArrayAdapter<String> mConversationArrayAdapter;
	// Name of the connected device
	private String mConnectedDeviceName = null;
	private String mConnectedDeviceAddress = null;
	// URL to send barcode to
	private String url;
	private long delay;
	
    private SQLiteDatabase db;
    private EventSender eventSender;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (D)
			Log.e(TAG, "+++ ON CREATE +++");

		setContentView(R.layout.activity_main);

		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		url = sharedPrefs.getString("pref_url", DEF_URL);
		delay = Long.parseLong(sharedPrefs.getString("pref_sleep", DEF_DELAY));

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}

        // Создаем соединение с БД
        DbHelper dbHelper = new DbHelper(getApplicationContext(), "EVENTS", null, 1);
        db = dbHelper.getWritableDatabase();
	}

	@Override
	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");

		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			if (mChatService == null)
				setupChat();
		}
		
        startEventSender();
	}

	@Override
	public synchronized void onResume() {
		super.onResume();
		if (D)
			Log.e(TAG, "+ ON RESUME +");

		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity
		// returns.
		if (mChatService != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// started already
			if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
				// Start the Bluetooth chat services
				mChatService.start();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			Intent i = new Intent(getApplicationContext(),
					SettingsActivity.class);
			startActivityForResult(i, SETTINGS_RESULT);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == SETTINGS_RESULT) {
			SharedPreferences sharedPrefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			url = sharedPrefs.getString("pref_url", DEF_URL);
			delay = Long.parseLong(sharedPrefs.getString("pref_sleep", DEF_DELAY));
            stopEventSender();
            startEventSender();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
        db.close();
		// Stop the Bluetooth chat services
		if (mChatService != null)
			mChatService.stop();
		if (D)
			Log.e(TAG, "--- ON DESTROY ---");
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
            return inflater.inflate(R.layout.fragment_main, container, false);
		}
	}

	private void setupChat() {
		Log.d(TAG, "setupChat()");

		// Initialize the array adapter for the conversation thread
		mConversationArrayAdapter = new ArrayAdapter<String>(this,
				R.layout.message);
        ListView mConversationView = (ListView) findViewById(R.id.in);
		mConversationView.setAdapter(mConversationArrayAdapter);

		// Initialize the BluetoothChatService to perform bluetooth connections
		mChatService = new BluetoothChatService(this, mHandler);
	}
	
	public void log(String message) {
		int count = mConversationArrayAdapter.getCount();
		if (count >= 10) 
			for (int i = 0; i < count - 10; i++)
				mConversationArrayAdapter.remove(mConversationArrayAdapter.getItem(i));
		mConversationArrayAdapter.add(message);
	}

	// The Handler that gets information back from the BluetoothChatService
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				if (D)
					Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothChatService.STATE_CONNECTED:
					mConversationArrayAdapter.clear();
					mConversationArrayAdapter.add(mConnectedDeviceName);
					break;
				case BluetoothChatService.STATE_CONNECTING:
					mConversationArrayAdapter.add("Connecting");
					break;
				case BluetoothChatService.STATE_LISTEN:
				case BluetoothChatService.STATE_NONE:
					mConversationArrayAdapter.add("Not connected");
					break;
				}
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				// construct a string from the valid bytes in the buffer
				String readMessage = new String(readBuf, 0, msg.arg1);
				log(mConnectedDeviceName + ":  " + readMessage);
                send(readMessage, System.currentTimeMillis());
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				mConnectedDeviceAddress = msg.getData().getString(DEVICE_ADDR);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
            case MESSAGE_LOG:
                log((String)msg.obj);
			}
		}
	};

    public void send(String barCode, long timestamp) {
        ContentValues values = new ContentValues();
        values.put("ts", timestamp);
        values.put("addr", mConnectedDeviceAddress);
        values.put("code", barCode.replaceAll("[\\r\\n]", ""));
        db.insert(DbHelper.TBL_EVENT, null, values);
    }

    private void startEventSender() {
        if (eventSender == null) {
            eventSender = new EventSender(getApplicationContext(), db, url, delay, mHandler);
            new Thread(eventSender).start();
        }
    }

    private void stopEventSender() {
        eventSender.stop();
        eventSender = null;
    }
}
