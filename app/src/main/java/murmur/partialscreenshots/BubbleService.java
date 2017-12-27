package murmur.partialscreenshots;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.functions.Action;
import io.reactivex.functions.BiFunction;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import murmur.partialscreenshots.databinding.BubbleLayoutBinding;
import murmur.partialscreenshots.databinding.ClipLayoutBinding;
import murmur.partialscreenshots.databinding.TrashLayoutBinding;

import static android.os.Environment.DIRECTORY_PICTURES;
import static android.widget.Toast.LENGTH_LONG;
import static murmur.partialscreenshots.MainActivity.sMediaProjection;

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
    private ImageReader mImageReader;
    private VirtualDisplay mVirtualDisplay;
    private MediaScannerConnection.OnScanCompletedListener mScanListener;
    private ImageReader.OnImageAvailableListener mImageListener;
    private int mX, mY, mW, mH;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("kanna","onStart");
        if(sMediaProjection != null){
            Log.d("kanna","mediaProjection alive");
        }
        initial();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (mWindowManager != null) {
            if (mBubbleLayoutBinding != null) {
                mWindowManager.removeView(mBubbleLayoutBinding.getRoot());
                mBubbleLayoutBinding = null;
                if (mBubbleLayoutParams != null) {
                    mBubbleLayoutParams = null;
                }
            }
            if (mTrashLayoutBinding != null) {
                mWindowManager.removeView(mTrashLayoutBinding.getRoot());
                mTrashLayoutBinding = null;
                if (mTrashLayoutParams != null) {
                    mTrashLayoutParams = null;
                }
            }
            if (mClipLayoutBinding != null) {
                if (isClipMode) {
                    mWindowManager.removeView(mClipLayoutBinding.getRoot());
                    isClipMode = false;
                }
                mClipLayoutBinding = null;
                if (mClipLayoutParams != null) {
                    mClipLayoutParams = null;
                }
            }
            mWindowManager = null;
            mBubbleHandler = null;
        }
        if (sMediaProjection != null) {
            sMediaProjection.stop();
            sMediaProjection = null;
        }
        super.onDestroy();
    }

    private void initial() {
        Log.d("kanna","initial");
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        mTrashLayoutBinding = TrashLayoutBinding.inflate(layoutInflater);
        if (mTrashLayoutParams == null) {
            mTrashLayoutParams = buildLayoutParamsForBubble(0, 0);
            mTrashLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        getWindowManager().addView(mTrashLayoutBinding.getRoot(), mTrashLayoutParams);
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);

        mBubbleHandler = new BubbleHandler(this);


        mBubbleLayoutBinding = BubbleLayoutBinding.inflate(layoutInflater);
        if (mBubbleLayoutParams == null) {
            mBubbleLayoutParams = buildLayoutParamsForBubble(60, 60);
        }
        mBubbleLayoutBinding.setHandler(mBubbleHandler);
        getWindowManager().addView(mBubbleLayoutBinding.getRoot(), mBubbleLayoutParams);

        isClipMode = false;
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
        } else if(Build.VERSION.SDK_INT >= 23) {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSPARENT);
        } else {
            //noinspection deprecation
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
        if (Build.VERSION.SDK_INT <= 22) {
            //noinspection deprecation
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_TOAST,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                    PixelFormat.TRANSPARENT);
        } else {
            Display display = getWindowManager().getDefaultDisplay();
            /*
            The real size may be smaller than the physical size of the screen
            when the window manager is emulating a smaller display (using adb shell wm size).
             */
            Point sizeReal = new Point();
            display.getRealSize(sizeReal);
            /*
            If requested from activity
            (either using getWindowManager() or (WindowManager) getSystemService(Context.WINDOW_SERVICE))
            resulting size will correspond to current app window size.
            In this case it can be smaller than physical size in multi-window mode.
             */
            Point size = new Point();
            display.getSize(size);
            int screenWidth, screenHeight, diff;
            if (size.x == sizeReal.x) {
                diff = sizeReal.y - size.y;
            } else {
                diff = sizeReal.x - size.x;
            }
            screenWidth = sizeReal.x + diff;
            screenHeight = sizeReal.y + diff;

            Log.d("kanna", "get screen " + screenWidth + " " + screenHeight
                    + " " + sizeReal.x + " " + size.x
                    + " " + sizeReal.y + " " + size.y);
            if (Build.VERSION.SDK_INT >= 26) {
                params = new WindowManager.LayoutParams(
                        screenWidth,
                        screenHeight,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSPARENT);
            } else {
                //noinspection deprecation
                params = new WindowManager.LayoutParams(
                        screenWidth,
                        screenHeight,
                        WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSPARENT);
            }

        }
        return params;
    }

    public void setTrashVisible() {
        if (mTrashLayoutBinding != null) {
            mTrashLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        }
    }

    public void checkRegion(MotionEvent motionEvent) {
        int location[] = new int[2];
        int trashLeft, trashRight, trashTop, trashBottom;
        float x, y;
        View trashView= mTrashLayoutBinding.getRoot();
        trashView.getLocationOnScreen(location);
        trashLeft = location[0];
        trashRight = trashLeft + trashView.getWidth();
        trashTop = location[1];
        trashBottom = trashTop + trashView.getHeight();
        x = motionEvent.getRawX();
        y = motionEvent.getRawY();
        Log.d("kanna",trashLeft + " " + trashRight + " " + trashTop + " " + trashBottom + " " + x + " " +y);
        if( x >= trashLeft && x <= trashRight && y >= trashTop && y <= trashBottom) {
            Log.d("kanna","stop self");
            stopSelf();
        } else {
            trashView.setVisibility(View.GONE);
        }
    }

    public void updateViewLayout(View view, WindowManager.LayoutParams params) {
        getWindowManager().updateViewLayout(view, params);
    }

    public void startClipMode() {
        if (mClipLayoutBinding == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(mContext);
            mClipLayoutBinding = ClipLayoutBinding.inflate(layoutInflater);
            mClipLayoutBinding.setHandler(mBubbleHandler);
        }
        mClipLayoutParams = buildLayoutParamsForClip();
        isClipMode = true;
        ((ClipView)mClipLayoutBinding.getRoot()).updateRegion(0, 0, 0, 0);
        mBubbleLayoutBinding.getRoot().setVisibility(View.GONE);
        getWindowManager().addView(mClipLayoutBinding.getRoot(), mClipLayoutParams);
        Toast.makeText(mContext,"Start clip mode.",Toast.LENGTH_SHORT).show();
    }

    public void finishClipMode(float inX1, float inY1, float inX2, float inY2) {
        updateClipBox(inX1, inY1, inX2, inY2);
        getWindowManager().removeView(mClipLayoutBinding.getRoot());
        isClipMode = false;
        if (mH < 50 || mW < 50) {
            Toast.makeText(mContext,"Region is too small.", Toast.LENGTH_SHORT).show();
            mBubbleLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        } else {
            screenshot();
        }
    }

    private void updateClipBox(float inX1, float inY1, float inX2, float inY2) {
        if(inX1 > inX2) {
            mX = (int)Math.ceil(inX2);
            mW = (int)Math.floor(inX1 - inX2);
        } else {
            mX = (int)Math.ceil(inX1);
            mW = (int)Math.floor(inX2 - inX1);
        }
        if (inY1 > inY2) {
            mY = (int)Math.ceil(inY2);
            mH = (int)Math.floor(inY1 - inY2);
        } else {
            mY = (int)Math.ceil(inY1);
            mH = (int)Math.floor(inY2 - inY1);
        }
        Log.d("kanna","clip " + mX + " " + mY + " " + mW + " " + mH);
    }

    private Bitmap createBitmap(Image image) {
        Log.d("kanna", "check create bitmap: " + Thread.currentThread().toString());
        Bitmap bitmap, bitmapCut;
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * image.getWidth();
        // create bitmap
        bitmap = Bitmap.createBitmap(image.getWidth() + rowPadding / pixelStride,
                image.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmapCut = Bitmap.createBitmap(bitmap, mX, mY, mW, mH);
        bitmap.recycle();
        image.close();
        return bitmapCut;
    }

    //https://stackoverflow.com/questions/14341041/how-to-get-real-screen-height-and-width
    private Flowable<Image> getScreenShot() {
        final Point screenSize = new Point();
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        Display display = getWindowManager().getDefaultDisplay();
        display.getRealSize(screenSize);
        return Flowable.create(new FlowableOnSubscribe<Image>() {
            @Override
            public void subscribe(@NonNull final FlowableEmitter<Image> emitter) throws Exception {
                mImageReader = ImageReader.newInstance(screenSize.x, screenSize.y, PixelFormat.RGBA_8888, 2);
                mVirtualDisplay = sMediaProjection.createVirtualDisplay("cap", screenSize.x, screenSize.y,
                        metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        mImageReader.getSurface(), null, null);
                mImageListener = new ImageReader.OnImageAvailableListener() {
                    Image image = null;
                    @Override
                    public void onImageAvailable(ImageReader imageReader) {
                        try {
                            image = imageReader.acquireLatestImage();
                            Log.d("kanna", "check reader: " + Thread.currentThread().toString());
                            if (image != null) {
                                emitter.onNext(image);
                                emitter.onComplete();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            emitter.onError(new Throwable("ImageReader error"));
                        }
                        mImageReader.setOnImageAvailableListener(null, null);
                    }

                };
                mImageReader.setOnImageAvailableListener(mImageListener, null);

            }
        }, BackpressureStrategy.DROP);
    }

    private Flowable<String> createFile() {
        return Flowable.create(new FlowableOnSubscribe<String>() {
            @Override
            public void subscribe(@NonNull FlowableEmitter<String> emitter) throws Exception {
                Log.d("kanna", "check create filename: " + Thread.currentThread().toString());
                String directory, fileHead, fileName;
                int count = 0;
                File externalFilesDir = Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES);
                if (externalFilesDir != null) {
                    directory = Environment.getExternalStoragePublicDirectory(DIRECTORY_PICTURES)
                            .getAbsolutePath() + "/screenshots/";

                    Log.d("kanna", directory);
                    File storeDirectory = new File(directory);
                    if (!storeDirectory.exists()) {
                        boolean success = storeDirectory.mkdirs();
                        if (!success) {
                            emitter.onError(new Throwable("failed to create file storage directory."));
                            return;
                        }
                    }
                } else {
                    emitter.onError(new Throwable("failed to create file storage directory," +
                            " getExternalFilesDir is null."));
                    return;
                }

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
                Calendar c = Calendar.getInstance();
                fileHead = simpleDateFormat.format(c.getTime()) + "_";
                fileName = directory + fileHead + count + ".png";
                File storeFile = new File(fileName);
                while (storeFile.exists()) {
                    count++;
                    fileName = directory + fileHead + count + ".png";
                    storeFile = new File(fileName);
                }
                emitter.onNext(fileName);
                emitter.onComplete();
            }
        }, BackpressureStrategy.DROP).subscribeOn(Schedulers.io());
    }

    private void writeFile(Bitmap bitmap, String fileName) throws IOException {
        Log.d("kanna", "check write file: " + Thread.currentThread().toString());
        FileOutputStream fos = new FileOutputStream(fileName);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        bitmap.recycle();
    }

    private Flowable<String> updateScan(final String fileName) {
        return Flowable.create(new FlowableOnSubscribe<String>() {
            @Override
            public void subscribe(@NonNull final FlowableEmitter<String> emitter) throws Exception {
                String[] path = new String[]{fileName};
                mScanListener = new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String s, Uri uri) {
                        Log.d("kanna", "check scan file: " + Thread.currentThread().toString());
                        if (uri == null) {
                            emitter.onError(new Throwable("Scan fail" + s));
                        }
                        else {
                            emitter.onNext(s);
                            emitter.onComplete();
                        }
                    }
                };
                MediaScannerConnection.scanFile(mContext, path, null, mScanListener);
            }
        }, BackpressureStrategy.DROP);
    }

    private void finalRelease(){
        if (mVirtualDisplay != null) {
            mVirtualDisplay.release();
        }
        if (mImageReader != null) {
            mImageReader = null;
        }
        if(mImageListener != null) {
            mImageListener = null;
        }
        if(mScanListener != null) {
            mScanListener = null;
        }
    }

    /*
    RXJava
     */
    private void shotScreen() {
        getScreenShot()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .map(new Function<Image, Bitmap>() {
                    @Override
                    public Bitmap apply(@NonNull Image image) throws Exception {
                        return createBitmap(image);
                    }
                })
                .zipWith(createFile(), new BiFunction<Bitmap, String, String>() {
                    @Override
                    public String apply(@NonNull Bitmap bitmap, @NonNull String fileName) throws Exception {
                        writeFile(bitmap, fileName);
                        return fileName;
                    }
                })
                .flatMap(new Function<String, Publisher<String>>() {
                    @Override
                    public Publisher<String> apply(@NonNull String fileName) throws Exception {
                        return updateScan(fileName);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(new Action() {
                    @Override
                    public void run() throws Exception {
                        Log.d("kanna", "check do finally: " + Thread.currentThread().toString());
                        mBubbleLayoutBinding.getRoot().setVisibility(View.VISIBLE);
                        finalRelease();
                    }
                })
                .subscribe(new Subscriber<String>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(String filename) {
                        Log.d("kanna", "onNext: " + filename);
                        Toast.makeText(mContext, "Create file: " + filename, LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.d("kanna", "onError: ", t);
                        Toast.makeText(mContext, "Error occur: " + t, LENGTH_LONG).show();
                    }

                    @Override
                    public void onComplete() {
                        Log.d("kanna", "onComplete");
                    }
                });
    }

    private void screenshot() {
        if (sMediaProjection != null) {
            shotScreen();
        } else {
            Toast.makeText(mContext, "No MediaProjection, stop bubble.", LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void stopClipMode() {
        getWindowManager().removeView(mClipLayoutBinding.getRoot());
        isClipMode = false;
        mBubbleLayoutBinding.getRoot().setVisibility(View.VISIBLE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isClipMode) {
            stopClipMode();
            Toast.makeText(mContext,"Configuration changed, stop clip mode.", Toast.LENGTH_SHORT).show();
        }
    }
}