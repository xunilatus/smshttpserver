package com.example.myserver;

import static android.content.ContentValues.TAG;
import android.content.Context;
import android.content.Intent;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.provider.Telephony;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import fi.iki.elonen.NanoHTTPD;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.FileNameMap;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import android.widget.Toast;


public class MainActivity extends AppCompatActivity {
    private boolean serverUp = false;
    private WebServer webServer;
    private TextView serverTextView, ipAddressTextView, textVw;
    private Button serverButton;
    String message, number;
    private WebView webView;
    final int SEND_SMS_PERMISSION_REQUEST_CODE = 1;
    private static final int READ_SMS_PERMISSION_CODE = 1;

    private ListView listView;

    public ArrayList<String> smsList = new ArrayList<>();

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        final int port = 8080;

        serverButton = (Button) findViewById(R.id.serverButton);
        serverTextView = (TextView) findViewById(R.id.serverTextView);
        ipAddressTextView = (TextView) findViewById(R.id.ipAddTextView);
        textVw = (TextView) findViewById(R.id.ipAdd);
        ipAddressTextView = (TextView) findViewById(R.id.ipAddTextView);

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.READ_SMS}, READ_SMS_PERMISSION_CODE);
        } else {
            readSms();
        }

        String wipAddress = getWifiIPAddress(getApplicationContext());
        if (wipAddress != null) {
            ipAddressTextView.setText(wipAddress);
        } else {
            ipAddressTextView.setText("No IP Address Found");
        }

        serverButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                serverUp = !serverUp;
                if (serverUp) {
                    startServer(port);
                } else {
                    stopServer();
                }
            }
        });
    }// Closes Saved instance

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public List<String> readSms() {
        List<String> smsList = new ArrayList<>();
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                null,
                null,
                null,
                null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String address = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                String body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY));
                smsList.add("Sender: " + address + "\nMessage: " + body);
            } while (cursor.moveToNext());
        }

        if (cursor != null) {
            cursor.close();
        }

        return smsList;  // Return the list of SMS messages
    }
   public void initializeSmsList() {
        smsList = new ArrayList<>(readSms()); // Populate smsList with the result of readSms()
    }



    public String getWifiIPAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            int ipAddress = wifiInfo.getIpAddress();
            return intToIp(ipAddress);
        }
        return null;
    }
    private String intToIp(int ipAddress) {
        return ((ipAddress & 0xFF) + "." +
                ((ipAddress >> 8) & 0xFF) + "." +
                ((ipAddress >> 16) & 0xFF) + "." +
                ((ipAddress >> 24) & 0xFF));
    }
    public void updateTextView(String newText) {
        if (newText != null) {
            textVw.setText(newText);
            //
        } else {
            // Handle null values gracefully, e.g., by setting an empty string
            textVw.setText("");
        }
    }

    private String streamToString(InputStream inputStream) {
        Scanner s = new Scanner(inputStream).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public void startServer(int port) {
        if (webServer == null) {
            webServer = new WebServer();
            try {
                webServer.start();
                serverTextView.setText(getString(R.string.server_running));
                serverButton.setText(getString(R.string.stop_server));
                // webView.loadUrl("http://localhost:8080");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stopServer() {
        if (webServer != null) {
            webServer.stop();
            webServer = null;
            serverTextView.setText(getString(R.string.server_down));
            serverButton.setText(getString(R.string.start_server));
        }
    }

    private class WebServer extends NanoHTTPD {
        public static final String
                MIME_PLAINTEXT = "text/plain",
                MIME_HTML = "text/html",
                MIME_JS = "application/javascript",
                MIME_CSS = "text/css",
                MIME_PNG = "image/png",
                MIME_DEFAULT_BINARY = "application/octet-stream",
                MIME_XML = "text/xml";
        private static final int PORT = 8080;
        private static final String TAG = "HttpServer";
        private AssetManager assetManager;
        private String message;

        public WebServer() {
            super(8080);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String uri = session.getUri();
            Log.d(TAG, "Requested URI: " + uri);
            assetManager = getAssets();
            InputStream inputStream;
            Response response = newChunkedResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, null);

            if (Method.POST.equals(session.getMethod()) && "/listview-data".equals(uri)) {
                try {
                    initializeSmsList();
                    String smsLogs = String.join("\n", smsList);
                    return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, smsLogs);
                }catch (Exception e) {
                    Log.e("HttpServer", "Error handling POST request", e);
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Server Error");
                }
            }

            if (Method.POST.equals(session.getMethod()) && "/message".equals(uri)) {
                try {
                    // Read content length from headers
                    Integer contentLength = Integer.parseInt(session.getHeaders().get("content-length"));
                    byte[] buf = new byte[contentLength];
                    session.getInputStream().read(buf, 0, contentLength);
                    String requestBody = new String(buf);
                    Log.d(TAG, "Request Body: " + requestBody);

                    // Parse form data from the request body
                    Map<String, String> formData = new HashMap<>();
                    String[] params = requestBody.split("&");
                    for (String param : params) {
                        String[] keyValue = param.split("=");
                        if (keyValue.length == 2) {
                            String key = URLDecoder.decode(keyValue[0], "UTF-8");
                            String value = URLDecoder.decode(keyValue[1], "UTF-8");
                            formData.put(key, value);
                        }
                    }
                    String number = formData.get("phoneNumber");
                    String message = formData.get("message");
                    Log.d(TAG, "Received message: " + message);

                    runOnUiThread(() -> sendSMSMessage(number, message));
                    runOnUiThread(() -> updateTextView(message));

                } catch (IOException | NumberFormatException e) {
                    e.printStackTrace();
                    return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error");
                }

            } else {

                try {
                    if (session.getMethod() == Method.GET && uri != null) {
                            inputStream = assetManager.open("html/index.html");
                            return newChunkedResponse(Response.Status.OK, MIME_HTML, inputStream);
                        }
                } catch (IOException e) {
                    Log.d(TAG, e.toString());
                }
            }
            return response;
        }// end of 'serve' function


    }// end of server



    protected void sendSMSMessage(String number, String message) {
        if (number == null || number.isEmpty()) {
            Log.e(TAG, "Invalid phone number: " + number);
            return;
        }

        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    android.Manifest.permission.SEND_SMS)) {
                // Explain why SMS permission is needed
            } else {
                // Request SMS permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        SEND_SMS_PERMISSION_REQUEST_CODE);
            }
        } else {
            // Permission already granted, send SMS
            try {
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(number, null, message, null, null);
                Toast.makeText(getApplicationContext(), "SMS sent.",
                        Toast.LENGTH_LONG).show();
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid destinationAddress: " + e.getMessage());
                e.printStackTrace();
                // Handle the error or display a message to the user
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case SEND_SMS_PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    SmsManager smsManager = SmsManager.getDefault();
                    smsManager.sendTextMessage(number, null, message, null, null);
                    Toast.makeText(getApplicationContext(), "SMS sent.",
                            Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(getApplicationContext(),
                            "SMS faild, please try again.", Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }
} // end of Main class
