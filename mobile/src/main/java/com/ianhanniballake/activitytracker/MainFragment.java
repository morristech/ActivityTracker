package com.ianhanniballake.activitytracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;

import java.util.concurrent.TimeUnit;

public class MainFragment extends Fragment implements GooglePlayServicesActivity.ConnectionListener {
    private static final String TAG = MainFragment.class.getSimpleName();
    private TextView mCurrentActivityView;
    private int mCurrentActivity = -1;
    private float mConfidence;
    private OnDataPointListener mDataSourceListener = new OnDataPointListener() {
        @Override
        public void onDataPoint(final DataPoint dataPoint) {
            mCurrentActivity = dataPoint.getValue(Field.FIELD_ACTIVITY).asInt();
            mConfidence = dataPoint.getValue(Field.FIELD_CONFIDENCE).asFloat();
            Log.d(TAG, "Got activity " + mCurrentActivity + " with confidence " + mConfidence);
            if (mCurrentActivityView == null) {
                return;
            }
            mCurrentActivityView.post(new Runnable() {
                @Override
                public void run() {
                    String text = getString(R.string.current_activity, getResources().getStringArray(R.array
                            .detected_activity)[mCurrentActivity], mConfidence);
                    mCurrentActivityView.setText(text);
                }
            });
        }
    };

    @Override
    public void onAttach(final Activity activity) {
        super.onAttach(activity);
        try {
            GooglePlayServicesActivity googlePlayServicesActivity = (GooglePlayServicesActivity) activity;
            googlePlayServicesActivity.setConnectionListener(this);
        } catch (ClassCastException e) {
            throw new IllegalStateException(activity.getClass().getSimpleName() + " must extend " +
                    GooglePlayServicesActivity.class.getSimpleName(), e);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);
        mCurrentActivityView = (TextView) view.findViewById(R.id.current_activity);
        if (mCurrentActivity != -1) {
            String text = getString(R.string.current_activity, getResources().getStringArray(R.array
                    .detected_activity)[mCurrentActivity], mConfidence);
            mCurrentActivityView.setText(text);
        }
        return view;
    }

    @Override
    public void onConnected(final GoogleApiClient googleApiClient) {
        // 2. Build a sensor registration request object
        SensorRequest req = new SensorRequest.Builder()
                .setDataType(DataType.TYPE_ACTIVITY_SAMPLE)
                .setAccuracyMode(SensorRequest.ACCURACY_MODE_HIGH)
                .setSamplingRate(1, TimeUnit.SECONDS)
                .build();
        // 3. Invoke the Sensors API with:
        // - The Google API client object
        // - The sensor registration request object
        // - The listener object
        PendingResult<Status> regResult = Fitness.SensorsApi.add(googleApiClient, req, mDataSourceListener);
        // 4. Check the result asynchronously
        regResult.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                if (status.isSuccess()) {
                    Log.d(TAG, "Sensor listener registered successfully");
                } else {
                    Log.e(TAG, "Sensor listener failed to register: " + status.getStatusMessage());
                }
            }
        });
        getActivity().startService(new Intent(getActivity(), SensorListenerRegisterService.class));
        // Make sure we are subscribed and capturing activity data
        PendingResult<Status> subscribePendingResult =
                Fitness.RecordingApi.subscribe(googleApiClient, DataType.TYPE_ACTIVITY_SAMPLE);
        subscribePendingResult.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(final Status status) {
                if (status.isSuccess()) {
                    Log.i(TAG, "Successfully subscribed!");
                } else {
                    Log.i(TAG, "There was a problem subscribing.");
                }
            }
        });
    }

    @Override
    public void onDisconnecting(GoogleApiClient googleApiClient) {
        Fitness.SensorsApi.remove(googleApiClient, mDataSourceListener);
    }

    @Override
    public void onDisconnected() {
        getActivity().startService(new Intent(getActivity(), SensorListenerRegisterService.class));
    }
}
