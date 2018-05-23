package murmur.partialscreenshots;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import io.reactivex.Single;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import murmur.partialscreenshots.databinding.BubbleLayoutBinding;
import murmur.partialscreenshots.databinding.ClipLayoutBinding;
import murmur.partialscreenshots.databinding.TrashLayoutBinding;

import static android.os.Environment.DIRECTORY_PICTURES;
import static murmur.partialscreenshots.MainActivity.sMediaProjection;

public class BubbleService extends Service {
    private WindowManager mWindowManager;
    private BubbleLayoutBinding mBubbleLayoutBinding;
    private WindowManager.LayoutParams mBubbleLayoutParams;
    private TrashLayoutBinding mTrashLayoutBinding;
    private WindowManager.LayoutParams mTrashLayoutParams;
    private ClipLayoutBinding mClipLayoutBinding;
    private int[] closeRegion = null;//left, top, right, bottom
    private boolean isClipMode;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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

    private void initial() {
        Log.d("kanna","initial");
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        mTrashLayoutBinding = TrashLayoutBinding.inflate(layoutInflater);
        if (mTrashLayoutParams == null) {
            mTrashLayoutParams = buildLayoutParamsForBubble(0, 0);
            mTrashLayoutParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        }
        getWindowManager().addView(mTrashLayoutBinding.getRoot(), mTrashLayoutParams);
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);

        mBubbleLayoutBinding = BubbleLayoutBinding.inflate(layoutInflater);
        if (mBubbleLayoutParams == null) {
            mBubbleLayoutParams = buildLayoutParamsForBubble(60, 60);
        }
        mBubbleLayoutBinding.setHandler(new BubbleHandler(this));
        getWindowManager().addView(mBubbleLayoutBinding.getRoot(), mBubbleLayoutParams);
    }

    private WindowManager getWindowManager() {
        if (mWindowManager == null) {
            mWindowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        }
        return mWindowManager;
    }

    public void checkInCloseRegion(float x, float y) {
        if (closeRegion == null) {
            int location[] = new int[2];
            View v = mTrashLayoutBinding.getRoot();
            v.getLocationOnScreen(location);
            closeRegion = new int[]{location[0], location[1],
                    location[0] + v.getWidth(), location[1] + v.getHeight()};
        }

        if (Float.compare(x, closeRegion[0]) >= 0 &&
                Float.compare(y, closeRegion[1]) >= 0 &&
                Float.compare(x, closeRegion[2]) <= 0 &&
                Float.compare(3, closeRegion[3]) <= 0) {
            stopSelf();
        } else {
            mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
        }
    }

    public void updateViewLayout(View view, WindowManager.LayoutParams params) {
        mTrashLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        getWindowManager().updateViewLayout(view, params);
    }

    public void startClipMode() {
        mTrashLayoutBinding.getRoot().setVisibility(View.GONE);
        isClipMode = true;
        if (mClipLayoutBinding == null) {
            LayoutInflater layoutInflater = LayoutInflater.from(this);
            mClipLayoutBinding = ClipLayoutBinding.inflate(layoutInflater);
            mClipLayoutBinding.setHandler(new ClipHandler(this));
        }
        WindowManager.LayoutParams mClipLayoutParams = buildLayoutParamsForClip();
        ((ClipView)mClipLayoutBinding.getRoot()).updateRegion(0, 0, 0, 0);
        mBubbleLayoutBinding.getRoot().setVisibility(View.GONE);
        getWindowManager().addView(mClipLayoutBinding.getRoot(), mClipLayoutParams);
        Toast.makeText(this,"Start clip mode.",Toast.LENGTH_SHORT).show();
    }

    public void finishClipMode(int[] clipRegion) {
        isClipMode = false;
        getWindowManager().removeView(mClipLayoutBinding.getRoot());
        if (clipRegion[2] < 50 || clipRegion[3] < 50) {
            Toast.makeText(this,"Region is too small.", Toast.LENGTH_SHORT).show();
            mBubbleLayoutBinding.getRoot().setVisibility(View.VISIBLE);
        } else {
            screenshot(clipRegion);
        }
    }

    public void screenshot(int[] clipRegion) {
        if (sMediaProjection != null) {
            shotScreen(clipRegion);
        } else {
            Toast.makeText(this,
                    "No MediaProjection, stop bubble.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    @SuppressLint("CheckResult")
    private void shotScreen(int[] clipRegion) {
        getScreenShot()
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .map(image -> createBitmap(image, clipRegion))
                .zipWith(createFile(), (bitmap, fileName) -> {
                    writeFile(bitmap, fileName);
                    return fileName;
                })
                .flatMap(this::updateScan)
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(() -> {
                    Log.d("kanna", "do finally: " + Thread.currentThread().toString());
                    mBubbleLayoutBinding.getRoot().setVisibility(View.VISIBLE);
                })
                .subscribe(
                        fileName -> {
                            Log.d("kanna", "onSuccess: " + fileName);
                            Toast.makeText(this, "Create file: " +
                                    fileName, Toast.LENGTH_LONG).show();
                        },
                        throwable -> {
                            Log.d("kanna", "onError: ", throwable);
                            Toast.makeText(this, "Error occur: " +
                                    throwable, Toast.LENGTH_LONG).show();
                        }
                );
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (isClipMode) {
            isClipMode = false;
            getWindowManager().removeView(mClipLayoutBinding.getRoot());
            Toast.makeText(this,"Configuration changed, stop clip mode.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        if (mWindowManager != null) {
            if (mBubbleLayoutBinding != null) {
                mWindowManager.removeView(mBubbleLayoutBinding.getRoot());
            }
            if (mTrashLayoutBinding != null) {
                mWindowManager.removeView(mTrashLayoutBinding.getRoot());
            }
            if (mClipLayoutBinding != null) {
                if (isClipMode) {
                    mWindowManager.removeView(mClipLayoutBinding.getRoot());
                }
            }
            mWindowManager = null;
        }
        if (sMediaProjection != null) {
            sMediaProjection.stop();
            sMediaProjection = null;
        }
        super.onDestroy();
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

    //https://stackoverflow.com/questions/14341041/how-to-get-real-screen-height-and-width
    private Single<Image> getScreenShot() {
        final Point screenSize = new Point();
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        Display display = getWindowManager().getDefaultDisplay();
        display.getRealSize(screenSize);
        return Single.create(emitter -> {
            ImageReader imageReader = ImageReader.newInstance(screenSize.x, screenSize.y,
                    PixelFormat.RGBA_8888, 2);
            sMediaProjection.createVirtualDisplay("cap", screenSize.x, screenSize.y,
                    metrics.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
            ImageReader.OnImageAvailableListener mImageListener =
                    new ImageReader.OnImageAvailableListener() {
                Image image = null;
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    try {
                        image = imageReader.acquireLatestImage();
                        Log.d("kanna", "reader: " + Thread.currentThread().toString());
                        if (image != null) {
                            emitter.onSuccess(image);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        emitter.onError(new Throwable("ImageReader error"));
                    }
                    imageReader.setOnImageAvailableListener(null, null);
                }

            };
            imageReader.setOnImageAvailableListener(mImageListener, null);
        });
    }

    private Single<String> updateScan(final String fileName) {
        return Single.create(emitter -> {
            String[] path = new String[]{fileName};
            MediaScannerConnection.scanFile(this, path, null, (s, uri) -> {
                Log.d("kanna", "scan file: " + Thread.currentThread().toString());
                if (uri == null) {
                    emitter.onError(new Throwable("Scan fail" + s));
                } else {
                    emitter.onSuccess(s);
                }
            });
        });
    }

    private Bitmap createBitmap(Image image, int[] clipRegion) {
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
        bitmapCut = Bitmap.createBitmap(bitmap,
                clipRegion[0], clipRegion[1], clipRegion[2], clipRegion[3]);
        bitmap.recycle();
        image.close();
        return bitmapCut;
    }

    private void writeFile(Bitmap bitmap, String fileName) throws IOException {
        Log.d("kanna", "check write file: " + Thread.currentThread().toString());
        FileOutputStream fos = new FileOutputStream(fileName);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
        bitmap.recycle();
    }

    private Single<String> createFile() {
        return Single.create((SingleOnSubscribe<String>) emitter -> {
            Log.d("kanna", "check create filename: " + Thread.currentThread().toString());
            String directory, fileHead, fileName;
            int count = 0;
            File externalFilesDir = Environment.
                    getExternalStoragePublicDirectory(DIRECTORY_PICTURES);
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

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd",
                    Locale.ENGLISH);
            Calendar c = Calendar.getInstance();
            fileHead = simpleDateFormat.format(c.getTime()) + "_";
            fileName = directory + fileHead + count + ".png";
            File storeFile = new File(fileName);
            while (storeFile.exists()) {
                count++;
                fileName = directory + fileHead + count + ".png";
                storeFile = new File(fileName);
            }
            emitter.onSuccess(fileName);
        }).subscribeOn(Schedulers.io());
    }
}