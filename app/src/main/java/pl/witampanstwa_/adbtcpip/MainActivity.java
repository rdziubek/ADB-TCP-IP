package pl.witampanstwa_.adbtcpip;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AppCompatActivity;
//import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class MainActivity extends AppCompatActivity {

    private boolean startedSuccessfully = false, stoppedSuccessfully = false, suError = false;
    private String port, status;

    private ConstraintLayout clToggle;
    private TextView twStatus, twPort;
    private EditText etPort;

//    String TAG = "ADBTCPIPapr22";

    private void setThemeOff() {
        twStatus.setText(getString(R.string.status_off));
        twStatus.setTextColor(Color.WHITE);
        twPort.setVisibility(View.VISIBLE);
        etPort.setVisibility(View.VISIBLE);
    }

    private void setThemeOn() {
        twStatus.setText(status);
        twStatus.setTextColor(Color.WHITE);
        twPort.setVisibility(View.GONE);
        etPort.setVisibility(View.GONE);
    }

    private String getIP() {
        try {
            for (Enumeration<NetworkInterface> en =
                 NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr =
                     intf.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() &&
                            !inetAddress.isLinkLocalAddress() &&
                            inetAddress.isSiteLocalAddress())
                        return inetAddress.getHostAddress();
                }
            }
        } catch (SocketException ex) {
            twStatus.setText(getString(R.string.status_reconnecting));
            return getIP();     //try to get the IP address once again since a network error had occurred
        }
        twStatus.setText(getString(R.string.status_reconnecting));
        return getIP();     //try to get the IP address once again since a network error had occurred
    }

    private void setPort() {
        port = etPort.getText().toString();
        if (port.equals(""))
            port = "5555";
    }

    private boolean checkWifiOnAndConnected() {
        WifiManager wifiMgr =
                (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        if (wifiMgr.isWifiEnabled()) { // Wi-Fi adapter is ON
            WifiInfo wifiInfo = wifiMgr.getConnectionInfo();
            return (wifiInfo.getNetworkId() != -1);    // false if not connected to an access point; true if connected to an access point
        } else {
            return false; // Wi-Fi adapter is OFF
        }
    }

    private String executeShellCommands() {
        if (!startedSuccessfully) {      //turn on adb
            try {
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());
                outputStream.writeBytes(String.format("setprop service.adb.tcp.port %s\n",
                        port));
                outputStream.flush();

                outputStream.writeBytes("stop adbd\n");
                outputStream.flush();

                outputStream.writeBytes("start adbd\n");
                outputStream.flush();
                outputStream.writeBytes("exit\n");
                outputStream.flush();

                try {
                    if (su.waitFor() != 0) {   //started successfully? | root access refused
                        suError = true;     //twStatus color will be red now
                        return getString(R.string.root_refused);
                    } else {
                        startedSuccessfully = true;
                        stoppedSuccessfully = false;
                    }
                } catch (InterruptedException e) {
                    suError = true;
                    return getString(R.string.root_refused);
                }
            } catch (IOException e) {   //device not rooted (possibly!)
                suError = true;
                return getString(R.string.root_not_detected);
            }
        } else {        //turn off adb (as startedSuccessfully was false)
            try {
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream outputStream = new DataOutputStream(su.getOutputStream());

                outputStream.writeBytes("stop adbd\n");
                outputStream.flush();

                outputStream.writeBytes("exit\n");
                outputStream.flush();

                try {
                    if (su.waitFor() != 0) {   //stopped successfully?
                        suError = true;
                        return getString(R.string.root_refused);
                    } else {
                        stoppedSuccessfully = true;
                        startedSuccessfully = false;
                    }
                } catch (InterruptedException e) {
                    suError = true;
                    return getString(R.string.root_refused);
                }
            } catch (IOException e) {    //device not rooted (possibly!)
                suError = true;
                return getString(R.string.root_not_detected);
            }
        }

        return getString(R.string.working);    //everything executed successfully (method returns early only on errors (controlled ones))
    }

    private void restartEverythingExceptData() {
        Intent intent = new Intent(MainActivity.this,
                MainActivity.class);
        intent.putExtra("startedSuccessfully", startedSuccessfully);
        intent.putExtra("stoppedSuccessfully", stoppedSuccessfully);
        intent.putExtra("port", port);
        startActivity(intent);
        finish();
    }

    private final Thread shellCommands = new Thread(new Runnable() {
        @Override
        public void run() {
            final String result = executeShellCommands();

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    twStatus.setText(result);
                    twStatus.setTextColor(suError ? Color.RED : Color.WHITE);

                    if ((startedSuccessfully || stoppedSuccessfully) && !suError) {     //this has to be checked as there might have been an error (i.e. wifi error), and the theme shall then stay in the same state. Also, note the operator precedence: "||" > "&&" > "|".
                        //restart activity to change theme
                        restartEverythingExceptData();
                    }
                    suError = false;    //reset error status (everything has done executing now; time to reset the cycle). This necessarily has to be put there (not inside of the above "if", as the "if" is executed only when the suError is not true)
                }
            });
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        //!get values from last activity (theme on/off purposes)
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            startedSuccessfully = extras.getBoolean("startedSuccessfully");
            stoppedSuccessfully = extras.getBoolean("stoppedSuccessfully");
            port = extras.getString("port");
        }

        //check which theme to load
        setTheme(startedSuccessfully ? R.style.AppThemeOn : R.style.AppThemeOff);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        clToggle = findViewById(R.id.cltoggle);
        twStatus = findViewById(R.id.twstatus);
        etPort = findViewById(R.id.etport);
        twPort = findViewById(R.id.twport);

        /*
        TODO: Implement adb status check here.
         */

        //set the appearance of new activity launched after adb via wifi has been turned on, depending on received data:
        if (startedSuccessfully) {
            status = String.format("Turned ON\nat %s:%s", getIP(), port);
            setThemeOn();
        } else
            setThemeOff();
        clToggle.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                clToggle.setEnabled(false);     //disable the adb toggle in order to prevent accidental change of the script state during its execution
                twStatus.setTextColor(Color.WHITE);     //reset the color (new cycle starts)
                twStatus.setText(getString(R.string.status_requesting_root));

                if (checkWifiOnAndConnected()) {
                    setPort();

                    if (shellCommands.getState() == Thread.State.NEW) {     //if thread is not running
                        shellCommands.start();    //execute shell commands on the background thread (although these dont take much time too complete)
                        try {
                            shellCommands.join();    //wait until thread finishes execution
                        } catch (InterruptedException e) {
                            restartEverythingExceptData();
                        }
                    } else
                        restartEverythingExceptData();
                } else {
                    twStatus.setText(getString(R.string.no_wifi));
                    twStatus.setTextColor(Color.YELLOW);
                }

                clToggle.setEnabled(true);    //it is required to re-enable the adb toggle there, as the layout doesnt necessarily get refreshed by statements in the "if" construction below (it gets disabled as soon as clToggle.onClick() is invoked)
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //TODO: add settings (port number)
        if (id == R.id.menu_item_github) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("http://www.github.com/witampanstwa/"));
            startActivity(browserIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
