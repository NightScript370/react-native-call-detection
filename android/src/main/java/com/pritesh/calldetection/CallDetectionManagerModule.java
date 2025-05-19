package com.pritesh.calldetection;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import java.util.HashMap;
import java.util.Map;

public class CallDetectionManagerModule
        extends ReactContextBaseJavaModule
        implements Application.ActivityLifecycleCallbacks,
        CallDetectionPhoneStateListener.PhoneCallStateUpdate {

    private boolean wasAppInOffHook = false;
    private boolean wasAppInRinging = false;
    private final ReactApplicationContext reactContext;
    private TelephonyManager telephonyManager;
    private CallDetectionPhoneStateListener callDetectionPhoneStateListener;
    private Activity activity = null;

    public CallDetectionManagerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "CallDetectionManagerAndroid";
    }

    @ReactMethod
    public void startListener() {
        if (activity == null) {
            activity = getCurrentActivity();
            activity.getApplication().registerActivityLifecycleCallbacks(this);
        }

        telephonyManager = (TelephonyManager) this.reactContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        callDetectionPhoneStateListener = new CallDetectionPhoneStateListener(this);

        // Adapted from https://stackoverflow.com/a/71789261
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (reactContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                telephonyManager.registerTelephonyCallback(reactContext.getMainExecutor(), callStateListener);
            }
        } else {
            telephonyManager.listen(callDetectionPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }

    }

    @RequiresApi(api = android.os.Build.VERSION_CODES.S)
    private static abstract class CallStateListener extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        abstract public void onCallStateChanged(int state);
    }

    private final boolean callStateListenerRegistered = false;

    private final CallStateListener callStateListener = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) ?
            new CallStateListener() {
                @Override
                public void onCallStateChanged(int state) {
                    // Handle call state change
                    phoneCallStateUpdated(state, null);
                }
            }
            : null;

    @ReactMethod
    public void stopListener() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
           if(telephonyManager != null) {
                if(callStateListener != null) {
                    telephonyManager.unregisterTelephonyCallback(callStateListener);
                }
            }
        } else {
            if(telephonyManager  != null) {
                if(callDetectionPhoneStateListener != null){
                    telephonyManager.listen(callDetectionPhoneStateListener,
                            PhoneStateListener.LISTEN_NONE);
                }
            }
            callDetectionPhoneStateListener = null;
        }
        telephonyManager = null;
    }

    /**
     * @return a map of constants this module exports to JS. Supports JSON types.
     */
    public
    Map<String, Object> getConstants() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("Incoming", "Incoming");
        map.put("Offhook", "Offhook");
        map.put("Disconnected", "Disconnected");
        map.put("Missed", "Missed");
        return map;
    }

    // Activity Lifecycle Methods
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceType) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {

    }

    @Override
    public void onActivityPaused(Activity activity) {

    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    private void sendEvent(String phoneNumber) {
    if (reactContext.hasActiveCatalystInstance()) {
        reactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit("PhoneCallStateUpdate", phoneNumber);
    }
}

    @Override
    public void phoneCallStateUpdated(int state, String phoneNumber) {
        switch (state) {
            //Hangup
            case TelephonyManager.CALL_STATE_IDLE:
                if(wasAppInOffHook) { // if there was an ongoing call and the call state switches to idle, the call must have gotten disconnected
                    sendEvent("Disconnected");
                } else if(wasAppInRinging) { // if the phone was ringing but there was no actual ongoing call, it must have gotten missed
                     sendEvent("Missed");
                }

                //reset device state
                wasAppInRinging = false;
                wasAppInOffHook = false;
                break;
            //Outgoing
            case TelephonyManager.CALL_STATE_OFFHOOK:
                //Device call state: Off-hook. At least one call exists that is dialing, active, or on hold, and no calls are ringing or waiting.
                wasAppInOffHook = true;
                 sendEvent("Offhook");
                break;
            //Incoming
            case TelephonyManager.CALL_STATE_RINGING:
                // Device call state: Ringing. A new call arrived and is ringing or waiting. In the latter case, another call is already active.
                wasAppInRinging = true;
                sendEvent("Incoming");
                break;
        }
    }
}
