package com.innominds.hsafemdm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubMessageResult;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.MessageCallback;
import com.microsoft.azure.sdk.iot.device.MessageSentCallback;
import com.microsoft.azure.sdk.iot.device.exceptions.IotHubClientException;
import com.microsoft.azure.sdk.iot.device.twin.DesiredPropertiesCallback;
import com.microsoft.azure.sdk.iot.device.twin.DirectMethodPayload;
import com.microsoft.azure.sdk.iot.device.twin.DirectMethodResponse;
import com.microsoft.azure.sdk.iot.device.twin.MethodCallback;
import com.microsoft.azure.sdk.iot.device.twin.ReportedPropertiesUpdateResponse;
import com.microsoft.azure.sdk.iot.device.twin.SubscriptionAcknowledgedCallback;
import com.microsoft.azure.sdk.iot.device.twin.Twin;
import com.microsoft.azure.sdk.iot.device.twin.TwinCollection;
import com.microsoft.azure.sdk.iot.provisioning.device.*;
import com.microsoft.azure.sdk.iot.provisioning.device.internal.exceptions.ProvisioningDeviceClientException;
import com.microsoft.azure.sdk.iot.provisioning.security.SecurityProviderSymmetricKey;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class DPSService extends Service {
    private static final String TAG = "test-dps-service";
    private static Context context;
    private static DeviceClient deviceClient = null;
    private static String SCOPE_ID;
    private static String GLOBAL_ENDPOINT;
    private static String SYMMETRIC_KEY;
    private static String REGISTRATION_ID;
    private static final ProvisioningDeviceClientTransportProtocol PROVISIONING_DEVICE_CLIENT_TRANSPORT_PROTOCOL = ProvisioningDeviceClientTransportProtocol.HTTPS;
    private static final int MAX_TIME_TO_WAIT_FOR_REGISTRATION = 10000; // in milliseconds
    private static Twin twin;
    private static final int METHOD_SUCCESS = 200;
    public static final int METHOD_THROWS = 403;
    private static final int METHOD_NOT_DEFINED = 404;
    private static final int UNKNOWN_COMMAND = 205;

    private static String dps_scope_id;
    private static String dps_global_end_point;
    private static String dps_symmetric_key;
    private static String dps_registration_id;
    private static final String conf_file_name = "/data/dps.conf";

    static class ProvisioningStatus
    {
        ProvisioningDeviceClientRegistrationResult provisioningDeviceClientRegistrationInfoClient = new ProvisioningDeviceClientRegistrationResult();
        Exception exception;
    }

    static class ProvisioningDeviceClientRegistrationCallbackImpl implements ProvisioningDeviceClientRegistrationCallback
    {
        @Override
        public void run(ProvisioningDeviceClientRegistrationResult provisioningDeviceClientRegistrationResult, Exception exception, Object context)
        {
            if (context instanceof ProvisioningStatus)
            {
                ProvisioningStatus status = (ProvisioningStatus) context;
                status.provisioningDeviceClientRegistrationInfoClient = provisioningDeviceClientRegistrationResult;
                status.exception = exception;
            }
            else
            {
                System.out.println("Received unknown context");
                Log.e(TAG,"Received unknown context");
            }
        }
    }

    private static class MessageSentCallbackImpl implements MessageSentCallback
    {
        @Override
        public void onMessageSent(Message sentMessage, IotHubClientException exception, Object callbackContext)
        {
            IotHubStatusCode status = exception == null ? IotHubStatusCode.OK : exception.getStatusCode();
            System.out.println("Message received! Response status: " + status);
            Log.d(TAG,"Message received! Response status: "+status);
        }
    }

    public void dpsFunction(){
        Log.d(TAG,"Inside dpsFunction()");
        File conf_file = new File(conf_file_name);
        if(conf_file.exists()) {
            getDpsConfiguration();
            Log.d(TAG, "Beginning setup");
            SecurityProviderSymmetricKey securityClientSymmetricKey;
            securityClientSymmetricKey = new SecurityProviderSymmetricKey(SYMMETRIC_KEY.getBytes(StandardCharsets.UTF_8), REGISTRATION_ID);
            ProvisioningDeviceClient provisioningDeviceClient = null;
            try {
                ProvisioningStatus provisioningStatus = new ProvisioningStatus();
                provisioningDeviceClient = ProvisioningDeviceClient.create(GLOBAL_ENDPOINT, SCOPE_ID, PROVISIONING_DEVICE_CLIENT_TRANSPORT_PROTOCOL, securityClientSymmetricKey);
                provisioningDeviceClient.registerDevice(new ProvisioningDeviceClientRegistrationCallbackImpl(), provisioningStatus);
                while (provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() != ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ASSIGNED) {
                    if (provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ERROR ||
                            provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_DISABLED ||
                            provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_FAILED) {
                        provisioningStatus.exception.printStackTrace();
                        System.out.println("Registration error, bailing out ");
                        Log.d(TAG, "Registration error, bailing out");
                        break;
                    }
                    System.out.println("Waiting for Provisioning Service to register");
                    Log.d(TAG, "Waiting for Provisioning Service to register");
                    Thread.sleep(MAX_TIME_TO_WAIT_FOR_REGISTRATION);
                }

                if (provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getProvisioningDeviceClientStatus() == ProvisioningDeviceClientStatus.PROVISIONING_DEVICE_STATUS_ASSIGNED) {
                    System.out.println("IotHUb Uri : " + provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getIothubUri());
                    System.out.println("Device ID : " + provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getDeviceId());
                    Log.d(TAG, "IotHUb Uri : " + provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getIothubUri());
                    Log.d(TAG, "Device ID : " + provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getDeviceId());

                    // connect to iothub
                    String iotHubUri = provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getIothubUri();
                    String deviceId = provisioningStatus.provisioningDeviceClientRegistrationInfoClient.getDeviceId();
                    try {
                        deviceClient = new DeviceClient(iotHubUri, deviceId, securityClientSymmetricKey, IotHubClientProtocol.MQTT);
                        MessageCallbackMqtt callback = new MessageCallbackMqtt();
                        Counter counter = new Counter(0);
                        deviceClient.setMessageCallback(callback, counter);
                        deviceClient.open(false);
                        deviceClient.subscribeToDesiredProperties(new DPSService.DesiredPropertiesUpdatedHandler(), null);
                        deviceClient.subscribeToMethodsAsync(new DPSService.SampleMethodCallback(), null, new DPSService.DirectMethodStatusCallback(), null);
                        Message messageToSendFromDeviceToHub = new Message("Hello from test device of dps");
                        System.out.println("Sending message from device to IoT Hub...");
                        Log.d(TAG, "Sending message from device to IoT Hub...");
                        deviceClient.sendEventAsync(messageToSendFromDeviceToHub, new MessageSentCallbackImpl(), null);
                        initProps();
                    } catch (IOException e) {
                        System.out.println("Device client threw an exception: " + e.getMessage());
                        Log.e(TAG, "Device client threw an exception" + e.getMessage());
                        if (deviceClient != null) {
                            deviceClient.close();
                        }
                    }
                }
            } catch (ProvisioningDeviceClientException | InterruptedException e) {
                System.out.println("Provisioning Device Client threw an exception" + e.getMessage());
                Log.d(TAG, "Provisioning Device Client threw an exception" + e.getMessage());
                if (provisioningDeviceClient != null) {
                    provisioningDeviceClient.closeNow();
                }
            } catch (IotHubClientException e) {
                e.printStackTrace();
            }
        }else{
            Log.e(TAG,"dps configuration file does not exists!");
            Log.e(TAG,"push dps.conf file to /data/");
        }
    }

    protected static class Counter
    {
        protected int num;
        public Counter(int num)
        {
            this.num = num;
        }
        public int get()
        {
            return this.num;
        }
        public void increment()
        {
            this.num++;
        }

        @Override
        public String toString()
        {
            return Integer.toString(this.num);
        }
    }

    private static class AppMessageCallback implements MessageCallback {
        public IotHubMessageResult execute(Message msg, Object context) {
            Log.d(TAG,"Received message from hub in execute: "
                    + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
            return IotHubMessageResult.COMPLETE;
        }

        @Override
        public IotHubMessageResult onCloudToDeviceMessageReceived(Message message, Object callbackContext) {
            Log.d(TAG,"Received message from hub in onCloudToDeviceMessageReceived: "
                    + new String(message.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
            return IotHubMessageResult.COMPLETE;
        }
    }

    protected static class MessageCallbackMqtt implements com.microsoft.azure.sdk.iot.device.MessageCallback
    {
        public IotHubMessageResult onCloudToDeviceMessageReceived(Message msg, Object context)
        {
            Counter counter = (Counter) context;
            Log.d(TAG,
                    "Received message " + counter.toString()
                            + " with content: " + new String(msg.getBytes(), Message.DEFAULT_IOTHUB_MESSAGE_CHARSET));
            counter.increment();
            return IotHubMessageResult.COMPLETE;
        }
    }

    protected static class DirectMethodStatusCallback implements SubscriptionAcknowledgedCallback
    {
        public void onSubscriptionAcknowledged(IotHubClientException exception, Object context)
        {
            IotHubStatusCode status = exception == null ? IotHubStatusCode.OK : exception.getStatusCode();
            Log.d(TAG,"IoT Hub responded to device method operation with status " + status.name());
        }
    }

    private int executeHubCommand(String methodData) throws UnsupportedEncodingException, JSONException {
        Log.d(TAG, "Executing command received from IoT Hub");
        String payload = methodData;
        JSONObject obj = new JSONObject(payload);
        Log.d(TAG, "Received payload= "+payload);
        return METHOD_SUCCESS;
    }

    protected class SampleMethodCallback implements MethodCallback
    {
        @Override
        public DirectMethodResponse onMethodInvoked(String methodName, DirectMethodPayload methodData, Object context)
        {
            Log.d(TAG,"Method name= "+methodName);
            Log.d(TAG,"Method payload= "+methodData.getPayloadAsJsonString());
            DirectMethodResponse deviceMethodData;
            try {
                if(methodName.equals("start")) {
                    int status = executeHubCommand(methodData.getPayloadAsJsonString());
                    Log.d(TAG,"status received from executeHubCommand= "+status);
                    if(status == UNKNOWN_COMMAND)
                        deviceMethodData = new DirectMethodResponse(status, "unknown command");
                    else
                        deviceMethodData = new DirectMethodResponse(status, "executed " + methodName +" successfully");
                }else{
                    Log.e(TAG,"Unknown method");
                    deviceMethodData = new DirectMethodResponse(METHOD_NOT_DEFINED, "method not defined " + methodName);
                }
            } catch (Exception e) {
                Log.e(TAG,"Failed to return deviceMethodData!");
                e.printStackTrace();
                int status = METHOD_THROWS;
                deviceMethodData = new DirectMethodResponse(status, "method throws " + methodName);
            }
            return deviceMethodData;
        }
    }
    private class DesiredPropertiesUpdatedHandler implements DesiredPropertiesCallback
    {
        @Override
        public void onDesiredPropertiesUpdated(Twin desiredPropertyUpdateTwin, Object context)
        {
            Log.d(TAG,"Inside onDesiredPropertiesUpdated");
            Message messageToSendFromDeviceToHub =  new Message("Hello from iDhi device");
            Log.d(TAG,"Sending message from device to IoT Hub...");
            deviceClient.sendEventAsync(messageToSendFromDeviceToHub, new MessageSentCallbackImpl(), null);
            twin.getDesiredProperties().putAll(desiredPropertyUpdateTwin.getDesiredProperties());
            twin.getDesiredProperties().setVersion(desiredPropertyUpdateTwin.getDesiredProperties().getVersion());
            Log.d(TAG,"Received desired property update. Current twin:");
            Log.d(TAG, String.valueOf(twin));
            updateTelemetry(10);
            Message messageToSendFromDeviceToHub1 =  new Message("Hello from iDhi device again");
            Log.d(TAG,"Sending message from device to IoT Hub...");
            deviceClient.sendEventAsync(messageToSendFromDeviceToHub1, new MessageSentCallbackImpl(), null);
        }
    }

    private static void updateTelemetry(int interval){
        Log.d(TAG,"Updating telemetry Interval to "+interval);
        if(deviceClient!=null){
            try {
                twin = deviceClient.getTwin();
                TwinCollection reportedProperties = twin.getReportedProperties();
                reportedProperties.put("telemetryInterval",interval);
                ReportedPropertiesUpdateResponse response = deviceClient.updateReportedProperties(reportedProperties);
                twin.getReportedProperties().setVersion(response.getVersion());
            } catch (Exception e) {
                Log.e(TAG,"Failed to update telemetry interval!");
                e.printStackTrace();
            }
        }else{
            Log.e(TAG,"Device Client is null, can't update the telemetry interval!");
        }
    }

    private void initProps() {
        Log.d(TAG, "initProps called");
        Log.d(TAG,"Getting current twin");
        try {
            twin = deviceClient.getTwin();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IotHubClientException e) {
            e.printStackTrace();
        }
        Log.d(TAG,"Received current twin: "+twin);
        //Sending reported property
        TwinCollection reportedProperties = twin.getReportedProperties();
        reportedProperties.put("telemetryInterval", 5);
        try {
            ReportedPropertiesUpdateResponse response = deviceClient.updateReportedProperties(reportedProperties);
            twin.getReportedProperties().setVersion(response.getVersion());
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (IotHubClientException e) {
            e.printStackTrace();
        }
    }

    public DPSService() {
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate() of DPSService is called");
        super.onCreate();
        int NOTIFICATION_ID = (int) (System.currentTimeMillis() % 10000);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startMyOwnForeground();
        }
        Log.d(TAG, "Inside onCreate() after startForeground in DPS Service");
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void startMyOwnForeground() {
        String NOTIFICATION_CHANNEL_ID = "com.innominds.hsafe_dps";
        String channelName = "HSafe-DPS Service";
        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
        Notification notification = notificationBuilder.setOngoing(true)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("HSafe-DPS is running in background")
                .setPriority(NotificationManager.IMPORTANCE_MIN)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(2, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "DPS Service onStartCommand called");
        context = getApplicationContext();
        registerReceiver(nwBroadcastReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        return START_STICKY;
    }

    private BroadcastReceiver nwBroadcastReceiver = new BroadcastReceiver() {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive of nwBroadcastReceiver");
            if (intent.getExtras() != null) {
                ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
                NetworkInfo currentNetworkInfo = connectivityManager.getActiveNetworkInfo();
                if (currentNetworkInfo != null && currentNetworkInfo.isConnected()) {
                    if (currentNetworkInfo.isConnected()) {
                        Log.d(TAG, "Connected to Network!");
                        if (deviceClient == null) {
                            Log.d(TAG,"client is null");
                            dpsFunction();
                        }
                    }
                }else if (intent.getExtras().getBoolean(ConnectivityManager.EXTRA_NO_CONNECTIVITY, Boolean.FALSE)) {
                    Log.e(TAG,"Not Connected to Network! Check your Network connection!!");
                    deviceClient = null;
                }
            }
        }
    };

    @Override
    public void onDestroy(){
        Log.d(TAG,"onDestroy() of DPS Service is called");
        super.onDestroy();
        unregisterReceiver(nwBroadcastReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
    Log.d(TAG,"onBind called in DPS Service");
    return null;
    }

    private void getDpsConfiguration(){
        Log.d(TAG,"Getting DPS configuration details");
        try {
            FileReader fileReader = new FileReader(new File(conf_file_name));
            Log.d(TAG,"conf_path= "+conf_file_name);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            StringBuilder stringBuilder = new StringBuilder();
            String line = bufferedReader.readLine();
            while (line != null) {
                stringBuilder.append(line).append("\n");
                line = bufferedReader.readLine();
            }
            bufferedReader.close();
            String responce = stringBuilder.toString();
            Log.d(TAG, "Conf from file = " + responce);
            JSONObject jsonObject  = new JSONObject(responce);
            dps_scope_id = jsonObject.get("SCOPE_ID").toString();
            dps_global_end_point = jsonObject.get("GLOBAL_ENDPOINT").toString();
            dps_symmetric_key = jsonObject.get("SYMMETRIC_KEY").toString();
            dps_registration_id = jsonObject.get("REGISTRATION_ID").toString();
            Log.d(TAG,"dps_scope_id: "+dps_scope_id);
            Log.d(TAG,"dps_global_end_point: "+dps_global_end_point);
            Log.d(TAG,"dps_registration_id: "+dps_registration_id);
            Log.d(TAG,"dps_symmetric_key: "+dps_symmetric_key);
            SCOPE_ID = dps_scope_id;
            REGISTRATION_ID = dps_registration_id;
            GLOBAL_ENDPOINT = dps_global_end_point;
            SYMMETRIC_KEY = dps_symmetric_key;
        }catch (Exception e){
            Log.e(TAG,"Exception while reading DPS configurations");
            e.printStackTrace();
        }
    }
}