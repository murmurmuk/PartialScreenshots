package murmur.partialscreenshots;

import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;


public class BubbleHandler {
    public View.OnTouchListener mBubbleTouchListener;
    public View.OnTouchListener mClipViewTouchListener;
    private final BubbleService mBubbleService;

    BubbleHandler(BubbleService service) {
        mBubbleService = service;
        setBubbleTouchListener();
        setClipViewTouchListener();
    }

    private void setBubbleTouchListener() {
        mBubbleTouchListener = new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private float move;
            private boolean isMove = false;
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        move = 0;
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = motionEvent.getRawX();
                        initialTouchY = motionEvent.getRawY();
                        break;
                    case MotionEvent.ACTION_UP:
                        view.performClick();
                        if (isMove) {
                            mBubbleService.checkRegion(motionEvent);
                            isMove = false;
                        }
                        Log.d("kanna", "move " + move);
                        if (Math.abs(move)< 100) {
                            mBubbleService.startClipMode();
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        isMove = true;
                        mBubbleService.setTrashVisible();
                        params.x = initialX + (int) (motionEvent.getRawX() - initialTouchX);
                        params.y = initialY + (int) (motionEvent.getRawY() - initialTouchY);
                        move = motionEvent.getRawX() - initialTouchX + motionEvent.getRawY() - initialTouchY;
                        mBubbleService.updateViewLayout(view, params);
                        break;
                }
                return true;
            }
        };
    }

    private void updateCustomView(ClipView view, float x1, float y1, float x2, float y2) {
        if (x1 > x2 && y1 > y2) {
            view.updateRegion(x2, y2, x1, y1);
        } else if(y1 > y2) {
            view.updateRegion(x1, y2, x2, y1);
        } else if(x1 > x2) {
            view.updateRegion(x2, y1, x1, y2);
        } else {
            view.updateRegion(x1, y1, x2, y2);
        }
    }

    private void setClipViewTouchListener(){
        mClipViewTouchListener = new View.OnTouchListener() {
            private float x1, y1, x2, y2;
            private float rx1, ry1, rx2, ry2;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        x1 = x2 = event.getX();
                        rx1 = rx2 = event.getRawX();
                        y1 = y2 = event.getY();
                        ry1 = ry2 = event.getRawY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        x2 = event.getX();
                        rx2 = event.getRawX();
                        y2 = event.getY();
                        ry2 = event.getRawY();
                        updateCustomView((ClipView) view, x1, y1, x2, y2);
                        break;
                    case MotionEvent.ACTION_UP:
                        view.performClick();
                        mBubbleService.finishClipMode(rx1, ry1, rx2, ry2);
                        break;
                }
                //Log.d("kanna",x1 +" "+ y1 + " " + x2 + " " + y2);
                return true;
            }
        };
    }
}

