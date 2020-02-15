package com.healthsaas.edge.safe;

import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkRequest;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;

import com.healthsaas.edge.safe.SafeDb.SafeDb;
import com.healthsaas.edge.safe.HttpWebPortal.AsyncResponseCallback;
import com.healthsaas.edge.safe.SafeDb.SafeDbEntry;
import com.healthsaas.edge.safe.crypto.AES;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;

public class SafeService extends Service {
    public static final String TAG = "SafeSDK." + SafeService.class.getSimpleName();

    static final int MSG_SAFE_INIT = 1;
    static final int MSG_WAKE_LOCK = 2;
    static final int MSG_WAKE_LOCK_TMO = 3;
    static final int MSG_IDLE = 4;
    static final int MSG_START_DRAIN_TMO = 5;
    static final int MSG_TMO = 7;
    static final int MSG_DRAIN_SAFE = 20;
    static final int MSG_DRAIN_SAFE_START = 22;
    static final int MSG_DRAIN_SAFE_DONE = 23;
    static final long WAKE_LOCK_ON = 300000L;
    static final long WAKE_LOCK_PAUSE = 30000L;

    private SafeDb mSafeDb;
    private int drainCount = 0;
    private int errorCount = 0;
    private HttpWebPortal mWebPortal;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock = null;
    private static SafeService safeInstance = null;
    private Random r = new Random();
    private boolean haveInternet = true;
    private ConnectivityManager connMgr;

    private final IBinder mBinder = new LocalBinder();

    private Handler safeHandler;

    public static SafeService  getInstance() {
        return safeInstance;
    }

    public class LocalBinder extends Binder {
        @SuppressWarnings("unused")
        public SafeService getService() {
            return SafeService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        safeInstance = this;

        connMgr = (ConnectivityManager)this.getSystemService(CONNECTIVITY_SERVICE);

        mWebPortal = new HttpWebPortal(SafeApp.sAppContext.getApplicationContext());
        mWebPortal.start();

        HandlerThread mThread = new HandlerThread("EDGESafe", Process.THREAD_PRIORITY_FOREGROUND);
        mThread.start();
        Looper safeLooper = mThread.getLooper();
        safeHandler = new SafeHandler(safeLooper);

        mSafeDb = new SafeDb(safeInstance.getApplicationContext());
        mSafeDb.open();

        Message m = safeHandler.obtainMessage(MSG_SAFE_INIT);
        safeHandler.sendMessageDelayed(m, 1500);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: " + startId);
        if( intent != null ) {
            String action = intent.getAction();
            Log.d(TAG, "onStartCommand.action: " + action);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        cancelTMO();
        cancelWLTMO();
        safeHandler.getLooper().quit();
        if (mSafeDb != null) {
            mSafeDb.close();
            mSafeDb = null;
        }
        myWebPortalCallback = null;
        clearWakeLock();
        monitorNetwork(false);
        myNetworkCallback = null;
        super.onDestroy();
    }

    public class SafeHandler extends Handler {
        private SafeHandler(Looper looper) { super(looper); }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "handleMessage: " + msg.what);
            switch (msg.what) {
                case MSG_SAFE_INIT:
                    handleInitMsg();
                    break;
                case MSG_TMO:
                    handleTmoMsg(msg);
                    break;
                case MSG_WAKE_LOCK:
                    setWakeLock();
                    break;
                case MSG_WAKE_LOCK_TMO:
                    clearWakeLock();
                    break;
                case MSG_DRAIN_SAFE:
                    handleDrainSafeMsg();
                    break;
                case MSG_START_DRAIN_TMO:
                    SafeSDK.getInstance().drainSafe();
                    break;
                case MSG_IDLE:
                    SafeSDK.getInstance().setStatus("Ready");
                    cancelTMO();
                    break;
                default:
                    Log.d(TAG, "Unimplemented msg_id=" + msg.what);
                    break;
            }
        }

        private  void handleInitMsg() {
            Log.d(TAG, "Handling Init...");
            Message m = safeHandler.obtainMessage(MSG_WAKE_LOCK);
            safeHandler.sendMessageDelayed(m, r.nextInt(10000 - 5000) + 5000);
            monitorNetwork(true);
        }

        private void handleTmoMsg(Message msg) {
            int tmo_type = msg.arg1;
            switch( tmo_type) {
                case MSG_DRAIN_SAFE_START:
                    Log.d(TAG, "ERROR: Drain Safe Timeout...");
                    SafeSDK.getInstance().drainSafe();
                    break;
                case MSG_DRAIN_SAFE_DONE:
                    Log.e(TAG, "sync timeout");
                     break;

                default:
                    Log.e(TAG, "Unknown tmo_type: " + tmo_type);
                    break;
            }

        }

        private synchronized void handleDrainSafeMsg() {
            cancelStartDrainTMO();
            ArrayList<SafeDbEntry> allEntries = new ArrayList<>();
            mSafeDb.getAllEntries(allEntries, true);
            Log.i(TAG, "drainSafe: allEntries.size: " + allEntries.size());
            drainCount = errorCount = 0;
            if (allEntries.size() > 0) {
                if (connMgr.getActiveNetworkInfo() != null && connMgr.getActiveNetworkInfo().isConnected()) {
                    SafeSDK.getInstance().setStatus("Sending Data");
                    for (SafeDbEntry dbe : allEntries) {
                        drainCount++;
                        mSafeDb.updateSending(dbe.ID, 1);
                        try {
                            if (!sendPayload(dbe.ID, AES.decrypt(dbe.url), AES.decrypt(dbe.payload), AES.decrypt(dbe.headers))) {
                                drainCount--;
                                mSafeDb.updateSending(dbe.ID, 0);
                            }
                        } catch (IllegalBlockSizeException |
                                BadPaddingException |
                                InvalidKeyException |
                                InvalidAlgorithmParameterException |
                                NoSuchAlgorithmException |
                                NoSuchPaddingException e) {
                            drainCount--;
                            mSafeDb.updateSending(dbe.ID, 0);
                            e.printStackTrace();
                        }
                    }
                } else {
                    SafeSDK.getInstance().setStatus("Internet\nUnavailable");
                }
            } else {
                if (connMgr.getActiveNetworkInfo() != null && connMgr.getActiveNetworkInfo().isConnected()) {
                    SafeSDK.getInstance().setStatus("Ready");
                    cancelTMO();
                } else {
                    SafeSDK.getInstance().setStatus("Internet\nUnavailable");
                }
            }
        }
    }

    private void setWakeLock() {
        if (powerManager == null)
            powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire(WAKE_LOCK_ON * 2);
        Log.d(TAG, "Set WakeLock");
        Message msg = safeHandler.obtainMessage(MSG_WAKE_LOCK_TMO);
        safeHandler.sendMessageDelayed(msg, WAKE_LOCK_ON + (r.nextInt(10000 - 5000) + 5000));
    }

    private void clearWakeLock() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        Message msg = safeHandler.obtainMessage(MSG_WAKE_LOCK);
        safeHandler.sendMessageDelayed(msg, WAKE_LOCK_PAUSE + (r.nextInt(10000 - 5000) + 5000));
    }

    private void cancelTMO() {
        safeHandler.removeMessages(MSG_TMO);
    }

    private void cancelWLTMO() {
        safeHandler.removeMessages(MSG_WAKE_LOCK_TMO);
    }

    private void cancelStartDrainTMO() { safeHandler.removeMessages(MSG_START_DRAIN_TMO);}

    private void sendTMOinMs(final int tmo_type, final int delayMs, final Object obj) {
        Log.d(TAG, String.format("Set TMO of %d after %d ms", tmo_type, delayMs));
        Message msg = safeHandler.obtainMessage(MSG_TMO, tmo_type, 0, obj);
        safeHandler.sendMessageDelayed(msg, delayMs);
    }

    public void drainSafe() {
        cancelTMO();
        safeHandler.sendMessageDelayed(safeHandler.obtainMessage(MSG_DRAIN_SAFE), 1500);
        sendTMOinMs(MSG_DRAIN_SAFE_START, 91500, null);
    }

    private boolean sendPayload(long id, String url, String payload, String headers) {
        String[] sHeaders = headers.split("\\|");
        Map<String, String> mHeaders = new HashMap<>();
        for (String x : sHeaders) {
            String[] keyValue = x.split(":");
            mHeaders.put(keyValue[0], keyValue[1]);
        }
        return mWebPortal.post(id, url, mHeaders,  payload, myWebPortalCallback );
    }

    AsyncResponseCallback myWebPortalCallback = new AsyncResponseCallback() {
        @Override
        public void onResponse(HttpWebPortal.Response response) {
            _onResponse(response);
        }
        private void _onResponse(HttpWebPortal.Response response) {
            switch (response.getRequestCode()) {
                case HttpWebPortal.GET:
                    if (response.httpRequestSucceeded()) {
                        Log.i(TAG, "GET OK");
                    }
                    else
                    {
                        Log.i(TAG, "GET FAILED");
                    }
                    break;
                case HttpWebPortal.POST:
                    if (response.httpRequestSucceeded()) {
                        mSafeDb.deleteByID(response.getId());
                        Log.i(TAG, "Deleted ID: " + response.getId());
                        drainCount--;
                    }
                    else
                    {
                        errorCount++;
                        if (mSafeDb.updateSending(response.getId(), 0) == 0) {
                            Log.e(TAG, "_onResponse: failed to update safe db...");
                        }
                    }
                    if (drainCount - errorCount <= 0) {
                        cancelTMO();
                        if (errorCount > 0) {
                            SafeSDK.getInstance().setStatus("Data Send Error - Retrying...");
                            Message m = safeHandler.obtainMessage(MSG_DRAIN_SAFE);
                            safeHandler.sendMessageDelayed(m, 5000);
                        } else {
                            SafeSDK.getInstance().setStatus("Data Sent");
                            Message m = safeHandler.obtainMessage(MSG_IDLE);
                            safeHandler.sendMessageDelayed(m, 2000);
                        }
                        errorCount = drainCount = 0;
                    }
                    break;
            }
        }
    };

    public boolean insertPayload(String payload, String headers, String url) {
        boolean resp;
        long id = 0;
        try {
            id = mSafeDb.insert(new SafeDbEntry(AES.encrypt(payload), AES.encrypt(headers), AES.encrypt(url)));
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        }
        resp = (id > 0);
        if (resp)
            Log.i(TAG, "Inserted ID: " + id);
        cancelStartDrainTMO();
        safeHandler.sendMessageDelayed(safeHandler.obtainMessage(MSG_START_DRAIN_TMO), 10000);
        return resp;
    }

    private void monitorNetwork(boolean start) {
        if (connMgr != null) {
            haveInternet = false;
            if (connMgr.getActiveNetworkInfo() != null)
                haveInternet = connMgr.getActiveNetworkInfo().isConnected();
            if (start) {
                NetworkRequest.Builder nrb = new NetworkRequest.Builder();
                nrb.addCapability(NET_CAPABILITY_INTERNET);

                connMgr.registerNetworkCallback(nrb.build(), myNetworkCallback);
                if (haveInternet) {
                    SafeSDK.getInstance().drainSafe();
                } else {
                    if (SafeSDK.isLauncher)
                        SafeSDK.getInstance().setStatus("Internet Unavailable");
                }
            } else {
                connMgr.unregisterNetworkCallback(myNetworkCallback);
            }
        }
    }

    ConnectivityManager.NetworkCallback myNetworkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(Network network) {
            super.onAvailable(network);
            if (!haveInternet) {
                haveInternet = true;
            }
            if (!SafeSDK.isLauncher)
                SafeSDK.getInstance().drainSafe();
        }

        @Override
        public void onLost(Network network) {
            super.onLost(network);
            haveInternet = false;
            if (SafeSDK.isLauncher)
                SafeSDK.getInstance().setStatus("Internet Unavailable");
        }
    };

}
