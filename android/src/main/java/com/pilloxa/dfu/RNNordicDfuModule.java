
package com.pilloxa.dfu;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import androidx.annotation.Nullable;
import android.util.Log;
import com.facebook.react.bridge.*;
import com.facebook.react.modules.core.RCTNativeAppEventEmitter;
import no.nordicsemi.android.dfu.*;

public class RNNordicDfuModule extends ReactContextBaseJavaModule implements LifecycleEventListener {

    private final String dfuStateEvent = "DFUStateChanged";
    private final String progressEvent = "DFUProgress";
    private final String dfuLogEvent = "DFULogEvent";
    private static final String name = "RNNordicDfu";
    public static final String LOG_TAG = name;
    private final ReactApplicationContext reactContext;
    private Promise mPromise = null;

    public RNNordicDfuModule(ReactApplicationContext reactContext) {
        super(reactContext);
        reactContext.addLifecycleEventListener(this);
        this.reactContext = reactContext;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DfuServiceInitiator.createDfuNotificationChannel(reactContext);
        }
    }

    @ReactMethod
    public void startDFU(String address, String name, String filePath, Boolean keepBond, Boolean forceScanForNewAddress, Promise promise) {
        try {
            mPromise = promise;
            final DfuServiceInitiator starter = new DfuServiceInitiator(address)
                    .setKeepBond(keepBond);

            if (name != null) {
                starter.setDeviceName(name);
            }

            starter.setForceScanningForNewAddressInLegacyDfu(forceScanForNewAddress);
            starter.setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true);
            starter.setZip(filePath);
            final DfuServiceController controller = starter.start(this.reactContext, DfuService.class);
        }
        catch(Exception ex) {
            promise.reject("DFU_FAILED", ex);
        }
    }

    @Override
    public String getName() {
        return name;
    }


    private void sendEvent(String eventName, @Nullable WritableMap params) {
        getReactApplicationContext()
                .getJSModule(RCTNativeAppEventEmitter.class)
                .emit(eventName, params);
    }

    private void sendStateUpdate(String state, String deviceAddress) {
        WritableMap map = new WritableNativeMap();
        Log.d(LOG_TAG, "State: " + state);
        map.putString("state", state);
        map.putString("deviceAddress", deviceAddress);
        sendEvent(dfuStateEvent, map);
    }

    private void sendLogEvent(String deviceAddress, int level, String message) {
        WritableMap map = Arguments.createMap();
        map.putString("deviceAddress", deviceAddress);
        map.putInt("level", level);
        map.putString("message", message);
        sendEvent(dfuLogEvent, map);
    }

    @Override
    public void onHostResume() {
        DfuServiceListenerHelper.registerProgressListener(this.reactContext, mDfuProgressListener);
        DfuServiceListenerHelper.registerLogListener(this.reactContext, mDfuLogListener);
    }

    @Override
    public void onHostPause() {
    }

    @Override
    public void onHostDestroy() {
        DfuServiceListenerHelper.unregisterProgressListener(this.reactContext, mDfuProgressListener);
        DfuServiceListenerHelper.unregisterLogListener(this.reactContext, mDfuLogListener);
    }

    private final DfuLogListener mDfuLogListener = new DfuLogListener() {
        @Override
        public void onLogEvent(String deviceAddress, int level, String message) {
            switch(level) {
                case DfuBaseService.LOG_LEVEL_DEBUG:
                    Log.d(LOG_TAG, message);
                    break;
                case DfuBaseService.LOG_LEVEL_VERBOSE:
                    Log.v(LOG_TAG, message);
                    break;
                case DfuBaseService.LOG_LEVEL_WARNING:
                    Log.w(LOG_TAG, message);
                    break;
                case DfuBaseService.LOG_LEVEL_ERROR:
                    Log.e(LOG_TAG, message);
                    break;
                case DfuBaseService.LOG_LEVEL_INFO:
                case DfuBaseService.LOG_LEVEL_APPLICATION:
                default:
                    Log.i(LOG_TAG, message);
                    break;
            }

            sendLogEvent(deviceAddress, level, message);
        }
    };

    /**
     * The progress listener receives events from the DFU Service.
     * If is registered in onCreate() and unregistered in onDestroy() so methods here may also be called
     * when the screen is locked or the app went to the background. This is because the UI needs to have the
     * correct information after user comes back to the activity and this information can't be read from the service
     * as it might have been killed already (DFU completed or finished with error).
     */
    private final DfuProgressListener mDfuProgressListener = new DfuProgressListenerAdapter() {
        @Override
        public void onDeviceConnecting(final String deviceAddress) {
            sendStateUpdate("CONNECTING", deviceAddress);
        }

        @Override
        public void onDfuProcessStarting(final String deviceAddress) {
            sendStateUpdate("DFU_PROCESS_STARTING", deviceAddress);
        }

        @Override
        public void onEnablingDfuMode(final String deviceAddress) {
            sendStateUpdate("ENABLING_DFU_MODE", deviceAddress);
        }

        @Override
        public void onFirmwareValidating(final String deviceAddress) {
            sendStateUpdate("FIRMWARE_VALIDATING", deviceAddress);
        }

        @Override
        public void onDeviceDisconnecting(final String deviceAddress) {
            sendStateUpdate("DEVICE_DISCONNECTING", deviceAddress);
        }

        @Override
        public void onDfuCompleted(final String deviceAddress) {
            if (mPromise != null) {
                WritableMap map = new WritableNativeMap();
                map.putString("deviceAddress", deviceAddress);
                mPromise.resolve(map);
                mPromise = null;
            }
            sendStateUpdate("DFU_COMPLETED", deviceAddress);


            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    // if this activity is still open and upload process was completed, cancel the notification
                    final NotificationManager manager = (NotificationManager) reactContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(DfuService.NOTIFICATION_ID);
                }
            }, 200);

        }

        @Override
        public void onDfuAborted(final String deviceAddress) {
            sendStateUpdate("DFU_ABORTED", deviceAddress);
            if (mPromise != null) {
                mPromise.reject("2", "DFU ABORTED");
                mPromise = null;
            }

        }

        @Override
        public void onProgressChanged(final String deviceAddress, final int percent, final float speed, final float avgSpeed, final int currentPart, final int partsTotal) {
            WritableMap map = new WritableNativeMap();
            map.putString("deviceAddress", deviceAddress);
            map.putInt("percent", percent);
            map.putDouble("speed", speed);
            map.putDouble("avgSpeed", avgSpeed);
            map.putInt("currentPart", currentPart);
            map.putInt("partsTotal", partsTotal);
            sendEvent(progressEvent, map);

        }

        @Override
        public void onError(final String deviceAddress, final int error, final int errorType, final String message) {
            sendStateUpdate("DFU_FAILED", deviceAddress);
            if (mPromise != null) {
                mPromise.reject(Integer.toString(error), message);
                mPromise = null;
            }
        }
    };
}