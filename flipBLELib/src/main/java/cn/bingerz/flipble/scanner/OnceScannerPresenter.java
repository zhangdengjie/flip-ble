package cn.bingerz.flipble.scanner;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.bingerz.flipble.central.CentralManager;
import cn.bingerz.flipble.scanner.lescanner.LeScanCallback;
import cn.bingerz.flipble.utils.EasyLog;

/**
 * Created by hanson on 10/01/2018.
 */

@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public abstract class OnceScannerPresenter implements LeScanCallback {

    private Scanner mScanner;
    private long mScanDuration;
    private List<ScanDevice> mScanDevices = new ArrayList<>();

    private Handler mHandler = new Handler(Looper.getMainLooper());

    public OnceScannerPresenter(Scanner scanner, ScanRuleConfig config) {
        this.mScanner = scanner;
        this.mScanDuration = config.getScanDuration();
        if (mScanDuration <= 0) {
            mScanDuration = CentralManager.DEFAULT_BACKGROUND_SCAN_DURATION;
        }
    }

    @Override
    public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
        next(device, rssi, scanRecord);
    }

    @SuppressWarnings({"MissingPermission"})
    private void next(BluetoothDevice device, int rssi, byte[] scanRecord) {
        if (device == null) {
            EasyLog.d("onLeScan device is null.");
            return;
        }
        AtomicBoolean hasFound = new AtomicBoolean(false);
        for (ScanDevice scanDevice : mScanDevices) {
            if (scanDevice.getAddress().equals(device.getAddress())) {
                hasFound.set(true);
            }
        }
        if (!hasFound.get()) {
            EasyLog.i("Device detected: Name: %s  Mac: %s  Rssi: %d "
                    , device.getName(), device.getAddress(), rssi);
            ScanDevice scanDevice = new ScanDevice(device, rssi, scanRecord);
            mScanDevices.add(scanDevice);
            onScanning(scanDevice);
        }
    }

    public final void notifyScanStarted() {
        mScanDevices.clear();

        removeHandlerMsg();

        if (mScanDuration > 0) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mScanner != null) {
                        mScanner.stopLeScan();
                    }
                }
            }, mScanDuration);
        }

        onScanStarted();
    }

    public final void notifyScanStopped() {
        removeHandlerMsg();
        onScanFinished(mScanDevices);
    }

    public final void removeHandlerMsg() {
        mHandler.removeCallbacksAndMessages(null);
    }

    public abstract void onScanStarted();

    public abstract void onScanning(ScanDevice device);

    public abstract void onScanFinished(List<ScanDevice> scanDevices);

}
