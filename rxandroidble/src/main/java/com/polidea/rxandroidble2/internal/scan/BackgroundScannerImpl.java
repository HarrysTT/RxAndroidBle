package com.polidea.rxandroidble2.internal.scan;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;

import com.polidea.rxandroidble2.exceptions.BleScanException;
import com.polidea.rxandroidble2.internal.util.RxBleAdapterWrapper;
import com.polidea.rxandroidble2.scan.BackgroundScanner;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;

import java.util.ArrayList;
import java.util.List;

import bleshadow.javax.inject.Inject;

@TargetApi(Build.VERSION_CODES.O)
public class BackgroundScannerImpl implements BackgroundScanner {

    private static final String TAG = "BackgroundScanner";
    private static final int NO_ERROR = 0;
    private final RxBleAdapterWrapper rxBleAdapterWrapper;
    private final AndroidScanObjectsConverter scanObjectsConverter;
    private final InternalScanResultCreator internalScanResultCreator;
    private final InternalToExternalScanResultConverter internalToExternalScanResultConverter;

    @Inject
    BackgroundScannerImpl(RxBleAdapterWrapper rxBleAdapterWrapper,
                          AndroidScanObjectsConverter scanObjectsConverter,
                          InternalScanResultCreator internalScanResultCreator,
                          InternalToExternalScanResultConverter internalToExternalScanResultConverter) {
        this.rxBleAdapterWrapper = rxBleAdapterWrapper;
        this.scanObjectsConverter = scanObjectsConverter;
        this.internalScanResultCreator = internalScanResultCreator;
        this.internalToExternalScanResultConverter = internalToExternalScanResultConverter;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Override
    public void scanBleDeviceInBackground(@NonNull PendingIntent callbackIntent, ScanSettings scanSettings, ScanFilter... scanFilters) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "PendingIntent based scanning is available for Android O and higher only.");
            return;
        }

        Log.i(TAG, "Requesting pending intent based scan.");
        final List<android.bluetooth.le.ScanFilter> nativeScanFilters = scanObjectsConverter.toNativeFilters(scanFilters);
        final android.bluetooth.le.ScanSettings nativeScanSettings = scanObjectsConverter.toNativeSettings(scanSettings);
        final int scanStartResult = rxBleAdapterWrapper.startLeScan(nativeScanFilters, nativeScanSettings, callbackIntent);

        if (scanStartResult != 0) {
            final BleScanException bleScanException = new BleScanException(scanStartResult);
            Log.w(TAG, "Failed to start scan", bleScanException);
            throw bleScanException;
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @Override
    public void stopBackgroundBleScan(@NonNull PendingIntent callbackIntent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "PendingIntent based scanning is available for Android O and higher only.");
            return;
        }

        Log.i(TAG, "Stopping pending intent based scan.");
        rxBleAdapterWrapper.stopLeScan(callbackIntent);
    }

    @Override
    public List<ScanResult> onScanResultReceived(@NonNull Intent intent) {
        final int callbackType = intent.getIntExtra(BluetoothLeScanner.EXTRA_CALLBACK_TYPE, -1);
        final int errorCode = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, NO_ERROR);
        final List<android.bluetooth.le.ScanResult> nativeScanResults = extractScanResults(intent);
        ArrayList<ScanResult> scanResults = new ArrayList<>();

        if (errorCode == NO_ERROR) {
            for (android.bluetooth.le.ScanResult result : nativeScanResults) {
                scanResults.add(convertScanResultToRxAndroidBLEModel(callbackType, result));
            }

            return scanResults;
        } else {
            throw new BleScanException(errorCode);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<android.bluetooth.le.ScanResult> extractScanResults(@NonNull Intent intent) {
        return (List<android.bluetooth.le.ScanResult>) intent.getSerializableExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
    }

    private ScanResult convertScanResultToRxAndroidBLEModel(int callbackType, android.bluetooth.le.ScanResult result) {
        return internalToExternalScanResultConverter.apply(internalScanResultCreator.create(callbackType, result));
    }
}
