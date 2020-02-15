package com.healthsaas.feverscout;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.healthsaas.edge.safe.SafeSDK;
import com.healthsaas.feverscout.ble.BleConsts;
import com.healthsaas.feverscout.ble.BleData;
import com.healthsaas.feverscout.ble.BleDevice;
import com.healthsaas.feverscout.ble.BleManager;
import com.healthsaas.feverscout.dataModels.deviceObject;
import com.healthsaas.feverscout.dataModels.payloadObject;
import com.healthsaas.feverscout.vivalinkdb.BtDeviceDb;
import com.vivalnk.vdireader.VDIType;
import com.vivalnk.vdiutility.viLog;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.security.auth.login.LoginException;

/**
 *
 */
public class FeverScoutManager extends Service {

    private static final String TAG = FeverScoutManager.class.getSimpleName();

    static final String ACTION_START_FS_MGR = BuildConfig.APPLICATION_ID + ".StartFSManager";
    static final String ACTION_START_NEW_DATA_SCAN = BuildConfig.APPLICATION_ID + ".StartNewDataScan";
    static final String ACTION_STOP_NEW_DATA_SCAN = BuildConfig.APPLICATION_ID + ".StopNewDataScan";
    static final String ACTION_STOP_FS_MGR = BuildConfig.APPLICATION_ID + ".StopFSManager";

    private BleManager mBleManager;
    private int updates = 0;
    private long lastUpdateTime = 0;

    private LoadDataHandler mLoadDataHandler;

    public FeverScoutManager() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        Notification mNotification = new NotificationCompat.Builder(this, Constants.MGR_NAME)
                .setSmallIcon(R.drawable.hs_icon)
                .setContentTitle(Constants.MGR_NAME)
                .setContentText(Constants.MGR_RUNNING).build();

        startForeground(Constants.MGR_NOTIFY_ID, mNotification);

        mLoadDataHandler = new LoadDataHandler(this);
        VDI_init();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + startId);
        if( intent != null ) {
            String action = intent.getAction();

            switch (action) {
                case ACTION_START_NEW_DATA_SCAN:
                    Log.i(TAG, "onStartCommand: Start New Data Scan");
                    mBleManager = BleManager.getInstance(this.getApplicationContext());
                    mBleManager.setHandler(mLoadDataHandler);
                    mBleManager.getBleReader().startTemperatureUpdate();
                    break;
                case ACTION_STOP_NEW_DATA_SCAN:
                    Log.i(TAG, "onStartCommand: Stop New Data Scan");
                    mBleManager = BleManager.getInstance(this.getApplicationContext());
                    mBleManager.getBleReader().stopTemperatureUpdate();
                    break;
                case ACTION_STOP_FS_MGR:
                    Log.i(TAG, "onStartCommand: Service Stopping");
                    stopSelf();
                    break;
                case ACTION_START_FS_MGR:
                    Log.i(TAG, "onStartCommand: Service Starting");
                    break;
                default:
                    Log.i(TAG, "onStartCommand: unknown action");
                    break;
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private static class LoadDataHandler extends Handler {
        private WeakReference<FeverScoutManager> mTarget;
        private float mLastTemp;
        private int mLastBattery;
        private VDIType.VDI_TEMPERATURE_STATUS mLastStatus;
        private long mLastSend;

        public LoadDataHandler(FeverScoutManager manager) {
            mTarget = new WeakReference<>(manager);
            mLastSend = 0;
            mLastStatus = VDIType.VDI_TEMPERATURE_STATUS.TASTATUS_WARMUP;
            mLastBattery = 0;
            mLastTemp = 0;
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            FeverScoutManager manager = mTarget.get();

            if (manager == null) {
                return;
            }
            switch (msg.what) {
                case BleConsts.MESSAGE_TEMPERATURE_UPDATE:
                    BleData data = (BleData) msg.obj;
                    float tempC = MainActivity.castOneDecimalFloat(data.getFinalTemperatureValue());
                    float tempF = MainActivity.castOneDecimalFloat((tempC * 9) / 5) + 32;
                    if ((tempF >= 88.6 && tempF <=108.6  // make sure temp is within +/- 10.0 of normal.
                            && data.getTemperatureStatus() != VDIType.VDI_TEMPERATURE_STATUS.TASTATUS_WARMUP) // ignore 'warmup' measurements
                            &&  (Math.abs(tempF - mLastTemp) >= 0.2 // need a variance >= 0.2 - otherwise it is way too chatty...
                            || data.getBatteryPercent() != mLastBattery // a change in battery level
                            || data.getTemperatureStatus() != mLastStatus // a change is status
                            || System.currentTimeMillis() >= mLastSend + 20*60*1000)) {
                        // only send deltas and every 20m heartbeat...
                        mLastTemp = tempF;
                        mLastBattery = data.getBatteryPercent();
                        mLastStatus = data.getTemperatureStatus();
                        mLastSend = System.currentTimeMillis();
                        data.setReadingTime(System.currentTimeMillis());
                        manager.insertData(data);
                        manager.updates++;
                        manager.lastUpdateTime = System.currentTimeMillis();
                        manager.saveConnectInfo();
                        // TODO: need to update UI...
                        //manager.updateDisplay();
                    }
                    Log.i(TAG, "MESSAGE_TEMPERATURE_UPDATE: " + tempC + "c - " + tempF + "f - rssi: " + data.getRSSI() + " - Status: " + data.getTemperatureStatus());
                    break;
                case BleConsts.MESSAGE_CHARGER_INFO_UPDATED:
                    String chargerInfo = (String)msg.obj;
                    if (chargerInfo.contains("low")){
                        Toast.makeText(manager, "charger low battery!", Toast.LENGTH_LONG).show();
                    }
                    break;
                case BleConsts.MESSAGE_TEMPERATURE_MISSED:
                    Log.i(TAG, "handleMessage: MESSAGE_TEMPERATURE_MISSED - " + msg.obj);
                    break;
                case BleConsts.MESSAGE_DEVIE_LOST:
                    Log.i(TAG, "handleMessage: MESSAGE_DEVIE_LOST");
                    break;
                case BleConsts.MESSAGE_TEMPERATURE_ABNORAML:
                    Toast.makeText(manager, "abnormal low temperature!", Toast.LENGTH_LONG).show();
                    break;
                default:
                    break;
            }
        }
    }

    private void VDI_init() {
        mBleManager = BleManager.getInstance(this.getApplicationContext());
        VDIType.VDI_CHECKBLE_STATUS_TYPE checkResult = mBleManager.getBleReader().checkBle();
        Log.i(TAG, "VDI_init: Status: " + checkResult.toString());
        //set zero to disable this feature
        mBleManager.getBleReader().setLostThreshold(0);
    }

    synchronized void insertData(final BleData data) {
        if (data != null) {
            Gson gson = new GsonBuilder().disableHtmlEscaping().create();

            String model = Constants.VIVA_MODEL_FEVERSCOUT;

            payloadObject po = new payloadObject();
            po.dt = "BT";

            po.hi.IMEI = SafeSDK.getInstance().getIMEI();
            po.hi.SN = SafeSDK.getInstance().getSerialNumber();

            //po.d.rt = Utils.fmtISO8601_receiptTime(); // always same as measure time...
            po.d.bl = String.valueOf(data.getBatteryPercent());
            po.d.man = Constants.VIVA_MFG_NAME;
            po.d.mod = model;
            po.d.sn = data.getDeviceId().replace("/", "."); // the box serial number has a period, not a slash...;
            po.d.mt = Utils.getISO8601(data.getReadingTime());
            po.d.u = "c";
            if (data.getDummy()) {
                po.d.bs = "N/A";
            } else {
                po.d.bs = "ALT/ArmPit";
            }
            float tempC = MainActivity.castOneDecimalFloat(data.getFinalTemperatureValue()); // final temp from fever scout
            float tempF = MainActivity.castOneDecimalFloat(((tempC * 9) / 5) + 32);
            po.d.to = String.valueOf(MainActivity.castOneDecimalFloat(tempF));
            po.d.tr = String.valueOf(MainActivity.castOneDecimalFloat(data.getTemperatureValue())); // raw temp from Fever Scout
            po.d.t = String.valueOf(tempC);
            po.d.rssi = String.valueOf(data.getRSSI());

            String x = gson.toJson(po);
            //Log.i(TAG, x);
            String endpointURL = String.format("%s%s", FeverScoutSettings.getRootURL(), "sppost.svc/ga");
            boolean bInserted = SafeSDK.getInstance().insertData(x, "Content-type:application/json;charset=UTF-8", endpointURL);
            if (bInserted) {
                Log.d("insertData", "Inserted BT");
            } else {
                Log.e("insertData", "Failed to insert BT");
            }
            SafeSDK.getInstance().drainSafe();
        }
    }

    private void saveConnectInfo() {
        final SharedPreferences sharedPrefs = this.getApplicationContext().getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        sharedPrefs.edit()
                .putInt("TotalConnections", updates)
                .putLong("LastConnectTime", lastUpdateTime)
                .apply();
        Intent RTReturn = new Intent(MainActivity.RECEIVE_MSG);
        LocalBroadcastManager.getInstance(this).sendBroadcast(RTReturn);
    }
}
