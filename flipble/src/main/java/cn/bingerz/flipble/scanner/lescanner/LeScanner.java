package cn.bingerz.flipble.scanner.lescanner;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.MainThread;
import android.support.annotation.WorkerThread;

import cn.bingerz.easylog.EasyLog;
import cn.bingerz.flipble.scanner.ScanRuleConfig;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class LeScanner {

    private BluetoothAdapter mBluetoothAdapter;

    protected final ScanRuleConfig mScanRuleConfig;
    protected final LeScanCallback mLeScanCallback;

    private final Handler mScanHandler;
    private final HandlerThread mScanThread;

    private LeScanState mScanState = LeScanState.STATE_IDLE;

    protected LeScanner(BluetoothAdapter bluetoothAdapter, ScanRuleConfig config, LeScanCallback callback) {
        mBluetoothAdapter = bluetoothAdapter;
        mScanRuleConfig = config;
        mLeScanCallback = callback;

        mScanThread = new HandlerThread("LeScannerThread");
        mScanThread.start();
        mScanHandler = new Handler(mScanThread.getLooper());
    }

    public static LeScanner createScanner(BluetoothAdapter bluetoothAdapter, ScanRuleConfig config, LeScanCallback callback) {
        boolean useAndroidLScanner = false;
        boolean useAndroidOScanner = false;
        if (android.os.Build.VERSION.SDK_INT < 18) {
            EasyLog.w("Not supported prior to API 18.");
            return null;
        }

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            EasyLog.i("This is pre Android 5.0. We are using old scanning APIs");
            useAndroidLScanner = false;

        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            EasyLog.i("This is Android 5.0. We are using new scanning APIs");
            useAndroidLScanner = true;
        } else {
            EasyLog.i("Using Android O scanner");
            useAndroidOScanner = true;
        }

        if (useAndroidOScanner) {
            return new LeScannerForAndroidO(bluetoothAdapter, config, callback);
        } else if (useAndroidLScanner) {
            return new LeScannerForLollipop(bluetoothAdapter, config, callback);
        } else {
            return new LeScannerForJellyBeanMr2(bluetoothAdapter, config, callback);
        }
    }

    @MainThread
    public void scanLeDevice(boolean enable) {
        if (!isBluetoothOn()) {
            EasyLog.d(String.format("Not %s scan because bluetooth is off", enable ? "starting" : "stopping"));
            return;
        }
        if (enable) {
            if (getScanState() == LeScanState.STATE_IDLE) {
                mScanState = LeScanState.STATE_SCANNING;
                try {
                    startScan();
                } catch (Exception e) {
                    EasyLog.e("Exception starting scan. Perhaps Bluetooth is disabled or unavailable?");
                }
            } else {
                EasyLog.d("LeScanner is already starting.");
            }
        } else {
            mScanState = LeScanState.STATE_IDLE;
            stopScan();
        }
    }

    @MainThread
    public void destroy() {
        EasyLog.d("Destroying");
        if (isScanning()) {
            stopScan();
        }
        postToWorkerThread(false, new Runnable() {
            @WorkerThread
            @Override
            public void run() {
                EasyLog.d("Quitting scan thread");
                mScanThread.quit();
            }
        });
    }

    protected void postToWorkerThread(boolean isRemovePending, Runnable r) {
        if (mScanHandler != null) {
            if (isRemovePending) {
                mScanHandler.removeCallbacksAndMessages(null);
            }
            mScanHandler.post(r);
        }
    }

    protected abstract void startScan();

    protected abstract void stopScan();

    protected BluetoothAdapter getBluetoothAdapter() {
        return mBluetoothAdapter;
    }

    @SuppressLint("MissingPermission")
    protected boolean isBluetoothOn() {
        try {
            BluetoothAdapter bluetoothAdapter = getBluetoothAdapter();
            if (bluetoothAdapter != null) {
                return bluetoothAdapter.isEnabled();
            }
            EasyLog.w("Cannot get bluetooth adapter");
        } catch (SecurityException e) {
            EasyLog.w("SecurityException checking if bluetooth is on");
        }
        return false;
    }

    public LeScanState getScanState() {
        return mScanState;
    }

    public boolean isScanning() {
        return getScanState() == LeScanState.STATE_SCANNING;
    }
}
