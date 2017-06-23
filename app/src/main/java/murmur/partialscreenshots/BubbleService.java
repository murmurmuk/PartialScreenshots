package murmur.partialscreenshots;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import murmur.partialscreenshots.databinding.BubbleLayoutBinding;
import murmur.partialscreenshots.databinding.ClipLayoutBinding;
import murmur.partialscreenshots.databinding.TrashLayoutBinding;

import static android.widget.Toast.LENGTH_LONG;
import static murmur.partialscreenshots.MainActivity.sMediaProjection;

/**
 * Created by murmurmuk on 2017/6/20.
 */

public class BubbleService extends Service {


    private WindowManager mWindowManager;
    private Context mContext;
    private BubbleLayoutBinding mBubbleLayoutBinding;
    private WindowManager.LayoutParams mBubbleLayoutParams;
    private TrashLayoutBinding mTrashLayoutBinding;
    private WindowManager.LayoutParams mTrashLayoutParams;
    private ClipLayoutBinding mClipLayoutBinding;
    private WindowManager.LayoutParams mClipLayoutParams;
    private BubbleHandler mBubbleHandler;
    private boolean isClipMode;

    private String FILEHEAD;
    private String mFileName;
    private String STORE_DIRECTORY;
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplay;
    private Point mSize;
    private ImageAvailableListener mImageAvailableListener;
    private boolean IMAGES_PRODUCED;
    private MediaScannerConnection mediaScannerConnection;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private MediaProjectionStopCallback mMediaProjectionStopCallback;

    private int mX, mY, mW, mH;

    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;
            Bitmap bitmapCut = null;

            try {
                image = reader.acquireLatestImage();
                if (image != null && !IMAGES_PRODUCED) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * mSize.x;

                    // create bitmap
                    bitmap = Bitmap.createBitmap(mSize.x + rowPadding / pixelStride, mSize.y, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);
                    bitmapCut = Bitmap.createBitmap(bitmap, mX, mY, mW, mH);//x,y,w,h
                    int count = 0;
                    mFileName = STORE_DIRECTORY + FILEHEAD + count + ".png";
                    File storeFile = new File(mFileName);
                    while (storeFile.exists()) {
                        count++;
                        mFileName = STORE_DIRECTORY + FILEHEAD + count + ".png";
                        storeFile = new File(mFileName);
                    }
                    // write bitmap to a file
                    fos = new FileOutputStream(mFileName);
                    bitmapCut.compress(Bitmap.CompressFormat.PNG, 100, fos);

                    IMAGES_PRODUCED = true;
                    Log.e("kanna", "captured image: " + mFileName +  " w " + mSize.x + " h " + mSize.y);
                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.e("kanna", "captured image: error " +  e.toString());
                Toast.makeText(mContext,"error occur: " +e.toString(), Toast.LENGTH_SHORT).show();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }

                if (bitmap != null) {
                    bitmap.recycle();
                }

                if(bitmapCut != null){
                    bitmapCut.recycle();

                }

                if (image != null) {
                    image.close();
                }
                if(IMAGES_PRODUCED){
                    Log.d("kanna","done task");
                }
                else{
                    Log.d("kanna","task fail");
                }
                stopProjection();
            }
        }
    }
    private void stopProjection() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (sMediaProjection != null) {
                    //sMediaProjection.stop();
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);
                    if (mImageAvailableListener != null) mImageAvailableListener = null;
                    sMediaProjection.unregisterCallback(mMediaProjectionStopCallback);
                    if(IMAGES_PRODUCED) {
                        Log.d("kanna","update entry");
                        updateEntry();
                    }
                }
            }
        });
    }



    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        mHandlerThread = new HandlerThread("bubblethread");
        mHandlerThread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("kanna","onstart");
        if(sMediaProjection != null){
            Log.d("kanna","mediaprojtction alive");
        }
        initial();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy(){
        if(mWindowManager != null) {
            if(mBubbleLayoutBinding != null){
                mWindowManager.removeView(mBubbleLayoutBinding.getRoot());
                mBubbleLayoutBinding = null;
                if(mBubbleLayoutParams != null){
                    mBubbleLayoutParams = null;
                }
            }

            if(mTrashLayoutBinding != null){
                mWindowManager.removeView(mTrashLayoutBinding.getRoot());
                mTrashLayoutBinding = null;
                if(mTrashLayoutParams != null){
                    mTrashLayoutParams = null;
                }
            }
            if(mClipLayoutBinding != null){
                if(isClipMode) {
                    mWindowManager.removeView(mClipLayoutBinding.getRoot());
                    isClipMode = false;
                }
                mClipLayoutBinding = null;
                if(mClipLayoutParams != null){
                    mClipLayoutParams = null;
                }
            }
            mWindowManager = null;
            mBubbleHandler = null;
        }
        if(sMediaProjection != null){
            sMediaProjection.stop();
            sMediaProjection = null;
        }
        if(mHandler != null){
            mHandler = null;
        }
        if(mHandlerThread != null){
            mHandlerThread.quit();
        }
        super.onDestroy();
    }

    private void initial(){
        Log.d("kanna","initial");
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        mTrashLayoutBinding = TrashLayoutBinding.inflate(layoutInflater);
        if(mTrashLayoutParams == null) {
            mTrashLayoutParams = buildLayoutParamsForBubble(0, 0);
            mTrashLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        getWindowManager().addView(mTrashLayoutBinding.getRoot(), mTrashLayoutParams);
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);

        mBubbleHandler = new BubbleHandler(this);


        mBubbleLayoutBinding = BubbleLayoutBinding.inflate(layoutInflater);
        if(mBubbleLayoutParams == null) {
            mBubbleLayoutParams = buildLayoutParamsForBubble(60, 60);
        }
        mBubbleLayoutBinding.setHandler(mBubbleHandler);
        getWindowManager().addView(mBubbleLayoutBinding.getRoot(), mBubbleLayoutParams);

        isClipMode = false;

        mSize = new Point();
        // start capture handling thread
        mHandler = new Handler(mHandlerThread.getLooper());
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Calendar c = Calendar.getInstance();
        FILEHEAD = simpleDateFormat.format(c.getTime()) + "_";
        Log.d("kanna", FILEHEAD);
    }

    private WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        }
        return mWindowManager;
    }
    private WindowManager.LayoutParams buildLayoutParamsForBubble(int x, int y) {
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= 26) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        else if(Build.VERSION.SDK_INT >= 23){
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        else{
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_TOAST,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = x;
        params.y = y;
        return params;
    }

    private WindowManager.LayoutParams buildLayoutParamsForClip() {
        WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= 26) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        else if(Build.VERSION.SDK_INT >= 23){
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        else{
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_TOAST,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        }
        return params;
    }

    public void setmTrashVisible(){
        if(mTrashLayoutBinding != null){
            mTrashLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        }
    }

    public void checkRegion(MotionEvent motionEvent){
        int location[] = new int[2];
        int trashLeft, trashRight, trashTop, trashBottom;
        float x, y;
        View trashview= mTrashLayoutBinding.getRoot();
        trashview.getLocationOnScreen(location);
        trashLeft = location[0];
        trashRight = trashLeft + trashview.getWidth();
        trashTop = location[1];
        trashBottom = trashTop + trashview.getHeight();
        x = motionEvent.getRawX();
        y = motionEvent.getRawY();
        Log.d("kanna",trashLeft + " " + trashRight + " " + trashTop + " " + trashBottom + " " + x + " " +y);
        if( x >= trashLeft && x <= trashRight && y >= trashTop && y <= trashBottom){
            Log.d("kanna","stop self");
            stopSelf();
        }
        else{
            trashview.setVisibility(View.GONE);
        }
    }

    public void updateViewLayout(View view, WindowManager.LayoutParams params){
        getWindowManager().updateViewLayout(view, params);
    }

    public void startClipMode(){
        if(mClipLayoutBinding == null){
            LayoutInflater layoutInflater = LayoutInflater.from(mContext);
            mClipLayoutBinding = ClipLayoutBinding.inflate(layoutInflater);
            mClipLayoutBinding.setHandler(mBubbleHandler);
        }
        if(mClipLayoutParams == null){
            mClipLayoutParams = buildLayoutParamsForClip();
        }
        isClipMode = true;
        ((ClipView)mClipLayoutBinding.getRoot()).updateRegion(0, 0, 0, 0);
        mBubbleLayoutBinding.getRoot().setVisibility(View.GONE);
        getWindowManager().addView(mClipLayoutBinding.getRoot(), mClipLayoutParams);
        Toast.makeText(mContext,"Start clip mode.",Toast.LENGTH_SHORT).show();
    }

    public void stopClipMode(float inX1, float inY1, float inX2, float inY2){
        updateClipBox(inX1, inY1, inX2, inY2);
        getWindowManager().removeView(mClipLayoutBinding.getRoot());
        isClipMode = false;
        if(mH < 50 || mW < 50){
            Toast.makeText(mContext,"Region is too small.", Toast.LENGTH_SHORT).show();
        }
        else{
            screenshot();
        }
        mBubbleLayoutBinding.getRoot().setVisibility(View.VISIBLE);

    }

    private void updateClipBox(float inX1, float inY1, float inX2, float inY2){
        if(inX1 > inX2){
            mX = (int)Math.ceil(inX2);
            mW = (int)Math.floor(inX1 - inX2);
        }
        else{
            mX = (int)Math.ceil(inX1);
            mW = (int)Math.floor(inX2 - inX1);
        }
        if(inY1 > inY2){
            mY = (int)Math.ceil(inY2);
            mH = (int)Math.floor(inY1 - inY2);
        }
        else{
            mY = (int)Math.ceil(inY1);
            mH = (int)Math.floor(inY2 - inY1);
        }
        Log.d("kanna","clip " + mX + " " + mY + " " + mW + " " + mH);
    }

    //https://stackoverflow.com/questions/14341041/how-to-get-real-screen-height-and-width`
    private void createVirtualDisplay(){
        // display metrics
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        Display display = getWindowManager().getDefaultDisplay();
        display.getRealSize(mSize);
        mImageReader = ImageReader.newInstance(mSize.x, mSize.y, PixelFormat.RGBA_8888, 2);
        String VDNAME = "capturer";
        mVirtualDisplay = sMediaProjection.createVirtualDisplay(VDNAME, mSize.x, mSize.y, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mImageReader.getSurface(), null, mHandler);
        IMAGES_PRODUCED = false;
        mImageAvailableListener = new ImageAvailableListener();
        // create virtual display depending on device width / height
        mImageReader.setOnImageAvailableListener(mImageAvailableListener, null);
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback {
        @Override
        public void onStop() {
            Log.e("ScreenCapture", "stopping projection.");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mVirtualDisplay != null) mVirtualDisplay.release();
                    if (mImageReader != null) mImageReader.setOnImageAvailableListener(null, null);
                    if (mImageAvailableListener != null) mImageAvailableListener = null;
                    sMediaProjection.unregisterCallback(mMediaProjectionStopCallback);
                }
            });
        }
    }

    private void screenshot(){
        if (sMediaProjection != null) {
            File externalFilesDir = getExternalFilesDir(null);
            if (externalFilesDir != null) {
                STORE_DIRECTORY = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/screenshots/";

                Log.d("kanna", STORE_DIRECTORY);
                File storeDirectory = new File(STORE_DIRECTORY);
                if (!storeDirectory.exists()) {
                    boolean success = storeDirectory.mkdirs();
                    if (!success) {
                        Log.e("kanna", "failed to create file storage directory.");
                        return;
                    }
                }
            } else {
                Log.e("kanna", "failed to create file storage directory, getExternalFilesDir is null.");
                return;
            }


            createVirtualDisplay();
            mMediaProjectionStopCallback = new MediaProjectionStopCallback();
            sMediaProjection.registerCallback(mMediaProjectionStopCallback, mHandler);
        }
    }

    private void updateEntry(){
        if(mediaScannerConnection == null) {
            mediaScannerConnection = new MediaScannerConnection(this, new MediaScannerConnection.MediaScannerConnectionClient() {
                @Override
                public void onMediaScannerConnected() {
                    Log.d("kanna", "connected");
                    scan();
                }

                @Override
                public void onScanCompleted(String path, Uri uri) {
                    if (uri == null)
                        Log.d("kanna", "completed with fail " + path);
                    else {
                        Log.d("kanna", "completed " + uri.toString());
                        taskComplete();
                    }
                    mediaScannerConnection.disconnect();
                    mediaScannerConnection = null;
                }
            });
            mediaScannerConnection.connect();
        }
    }
    private void scan(){
        String tmp = mFileName;
        mediaScannerConnection.scanFile(tmp, null);
    }
    private void taskComplete(){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, "create file: "+mFileName, LENGTH_LONG).show();
            }
        });
    }


}

