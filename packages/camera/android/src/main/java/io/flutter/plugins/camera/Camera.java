package io.flutter.plugins.camera;

import android.hardware.camera2.CameraAccessException;

import androidx.annotation.NonNull;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel;

public interface Camera {

    void open(@NonNull final MethodChannel.Result result) throws CameraException;

    void takePicture(String filePath, @NonNull final MethodChannel.Result result);

    void startVideoRecording(String filePath, MethodChannel.Result result);

    void stopVideoRecording(@NonNull final MethodChannel.Result result);

    void startPreviewWithImageStream(EventChannel imageStreamChannel) throws CameraException;

    void startPreview() throws CameraException;

    void pauseVideoRecording(@NonNull final MethodChannel.Result result);

    void resumeVideoRecording(@NonNull final MethodChannel.Result result);

    void dispose();

    void close();
}
