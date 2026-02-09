package com.example.rotaoptjv; // Bunu kendi paket adınızla değiştirin

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.google.android.gms.maps.SupportMapFragment;

//Müşteri eklerken haritanın kontrollerini daha iyi yapmak için eklediğimiz fragment sınıfı Custom Fragment yani
public class CustomMapFragment extends SupportMapFragment {
    private OnTouchListener mListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        TouchableWrapper frameLayout = new TouchableWrapper(getActivity());
        frameLayout.setBackgroundColor(Color.TRANSPARENT);

        ((ViewGroup) view).addView(frameLayout,
                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        return view;
    }

    public void setListener(OnTouchListener listener) {
        mListener = listener;
    }

    public interface OnTouchListener {
        void onTouch();
        void onRelease();
    }

    public class TouchableWrapper extends FrameLayout {
        public TouchableWrapper(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mListener != null) mListener.onTouch();
                    break;
                case MotionEvent.ACTION_UP:
                    if (mListener != null) mListener.onRelease();
                    break;
            }
            return super.dispatchTouchEvent(event);
        }
    }
}