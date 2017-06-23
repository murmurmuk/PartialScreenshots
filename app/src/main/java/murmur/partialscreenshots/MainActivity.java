package murmur.partialscreenshots;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import murmur.partialscreenshots.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 5566;
    private static final int Overlay_REQUEST_CODE = 251;
    private static final int REQUEST_CODE = 55566;
    private ActivityMainBinding binding;
    private MediaProjectionManager mProjectionManager;
    public static MediaProjection sMediaProjection;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        checkWritePermission();

        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);


        binding.test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("kanna","hello world");
                startMediaProjection();
            }
        });
    }

    private void checkWritePermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Log.d("kanna","pass");
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed, we can request the permission.

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_READ_CONTACTS);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        }
    }

    private void startMediaProjection(){
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }


    private void checkDrawOverlayPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, Overlay_REQUEST_CODE);
            } else {
                startBubble();
            }
        } else {
            startBubble();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("kanna","get write permission");

                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.

                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "Need WRITE_EXTERNAL_STORAGE permission", Toast.LENGTH_LONG).show();
                }
                break;

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case Overlay_REQUEST_CODE: {
                if (Build.VERSION.SDK_INT >= 23) {
                    if (Settings.canDrawOverlays(this)) {
                        Log.d("kanna","get permission");
                        startBubble();
                    }
                    else{
                        Log.d("kanna","not permission");
                        Toast.makeText(this, "Need SYSTEM_ALERT_WINDOW permission", Toast.LENGTH_LONG).show();
                        checkDrawOverlayPermission();
                    }
                } else {
                    startBubble();
                }
                break;
            }
            case REQUEST_CODE:

                sMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);
                if(sMediaProjection == null){
                    Log.d("kanna", "not get permission of mediaprojection");
                    Toast.makeText(this, "Need MediaProjection", Toast.LENGTH_LONG).show();
                    startMediaProjection();
                }
                else {
                    checkDrawOverlayPermission();
                }
                break;
        }
    }
    @Override
    protected void onResume(){
        super.onResume();
        if(sMediaProjection == null){
            binding.test.setVisibility(View.VISIBLE);
        }
    }

    private void startBubble(){
        Log.d("kanna","start buubble");
        binding.test.setVisibility(View.GONE);
        Intent intent = new Intent(this, BubbleService.class);
        stopService(intent);
        startService(intent);
    }



}
