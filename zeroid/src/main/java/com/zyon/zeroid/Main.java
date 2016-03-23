package com.zyon.zeroid;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class Main extends Activity implements OnClickListener {
    private static final String TAG = Main.class.getSimpleName();
    private boolean D = true;

    //region Intent Declaration
    private static final int PREF_UPDATE = 1;
    //endregion

    private ZeroidPreferences zeroPrefs = null;

    Button btnRobot;
    Button btnController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        zeroPrefs = new ZeroidPreferences(getApplicationContext());

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }

        TextView tvMainTitle = (TextView) findViewById(R.id.tvMainTitle);
        tvMainTitle.setShadowLayer(1, 3, 3, Color.GRAY);

        btnRobot = (Button) findViewById(R.id.btnRobot);
        btnRobot.setOnClickListener(this);

        btnController = (Button) findViewById(R.id.btnController);
        btnController.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (D) Log.i(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (D) Log.i(TAG, "onResume");
        zeroPrefs.Resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (D) Log.i(TAG, "onPause");
        zeroPrefs.Pause();
    }

//    @Override
//    public boolean onCreateOptionsMenu(final Menu menu) {
//        super.onCreateOptionsMenu(menu);
//        zeroPrefs.Create(menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(final MenuItem item) {
//        super.onOptionsItemSelected(item);
//        if (item == zeroPrefs.SettingsMenuItem()) {
//            startActivity(new Intent(this, ZeroidPreferenceActivity.class));
//        } // if
//        return true;
//    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_MENU:
                //startActivity(new Intent(this, ZeroidPreferenceActivity.class));
                Intent enableIntent = new Intent(this,ZeroidPreferenceActivity.class);
                startActivityForResult(enableIntent, PREF_UPDATE);
                return true;
        }

        return super.onKeyDown(keycode, e);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
            case PREF_UPDATE:

                break;
        }
    }
    //endregion Activity

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnRobot:
                Intent intent_server = new Intent(this, ZeroidRobot.class);
                startActivity(intent_server);
                break;
            case R.id.btnController:
                Intent intent_client = new Intent(this, ZeroidController.class);
                startActivity(intent_client);
                break;
            default:
                break;
        }
    }
}
