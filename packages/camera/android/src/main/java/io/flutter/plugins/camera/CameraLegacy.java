package io.flutter.plugins.camera;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.view.TextureRegistry.SurfaceTextureEntry;

import static android.view.OrientationEventListener.ORIENTATION_UNKNOWN;

public class CameraLegacy implements io.flutter.plugins.camera.Camera {
    private static final AspectRatio DEFAULT_ASPECT_RATIO = AspectRatio.of(4, 3);
    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);
    private final SurfaceTextureEntry flutterTexture;
    //  private final CameraManager cameraManager;
    private final OrientationEventListener orientationEventListener;
    private final boolean isFrontFacing;
    private final int sensorOrientation;
    private final int cameraId;
    //  private final Size captureSize;
//  private final Size previewSize;
    private final boolean enableAudio;
    private final SizeMap mPreviewSizes = new SizeMap();
    private final SizeMap mPictureSizes = new SizeMap();

    private Camera camera;
    private Camera.PreviewCallback previewCallback;
    //  private CameraDevice cameraDevice;
//  private CameraCaptureSession cameraCaptureSession;
//  private ImageReader pictureImageReader;
//  private ImageReader imageStreamReader;
    private DartMessenger dartMessenger;
    //  private CaptureRequest.Builder captureRequestBuilder;
    private MediaRecorder mediaRecorder;
    private boolean recordingVideo;
    private CamcorderProfile recordingProfile;
    private int currentOrientation = ORIENTATION_UNKNOWN;
    private Camera.Parameters cameraParameters;

    // Mirrors camera.dart
    public enum ResolutionPreset {
        low,
        medium,
        high,
        veryHigh,
        ultraHigh,
        max,
    }

    public CameraLegacy(
            final Activity activity,
            final SurfaceTextureEntry flutterTexture,
            final DartMessenger dartMessenger,
            final String cameraName,
            final String resolutionPreset,
            final boolean enableAudio) {
        if (activity == null) {
            throw new IllegalStateException("No activity available!");
        }

        int cameraId;
        try {
            cameraId = Integer.parseInt(cameraName);
        } catch (NumberFormatException e) {
            cameraId = 0;
        }
        this.cameraId = cameraId;
        this.enableAudio = enableAudio;
        this.flutterTexture = flutterTexture;
        this.dartMessenger = dartMessenger;
//    this.cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        orientationEventListener =
                new OrientationEventListener(activity.getApplicationContext()) {
                    @Override
                    public void onOrientationChanged(int i) {
                        if (i == ORIENTATION_UNKNOWN) {
                            return;
                        }
                        // Convert the raw deg angle to the nearest multiple of 90.
                        currentOrientation = (int) Math.round(i / 90.0) * 90;
                    }
                };
        orientationEventListener.enable();
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, cameraInfo);

//    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraName);
//    StreamConfigurationMap streamConfigurationMap =
//        characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
//    //noinspection ConstantConditions
        sensorOrientation = cameraInfo.orientation;
//    //noinspection ConstantConditions
        isFrontFacing = cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT;
//    ResolutionPreset preset = ResolutionPreset.valueOf(resolutionPreset);
//    recordingProfile =
//        CameraUtils.getBestAvailableCamcorderProfileForResolutionPreset(cameraName, preset);
//    captureSize = new Size(recordingProfile.videoFrameWidth, recordingProfile.videoFrameHeight);
//    previewSize = computeBestPreviewSize(cameraName, preset);
    }

    private void prepareMediaRecorder(String outputFilePath) throws IOException {
        if (mediaRecorder != null) {
            mediaRecorder.release();
        }
        mediaRecorder = new MediaRecorder();

        // There's a specific order that mediaRecorder expects. Do not change the order
        // of these function calls.
        if (enableAudio) mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(recordingProfile.fileFormat);
        if (enableAudio) mediaRecorder.setAudioEncoder(recordingProfile.audioCodec);
        mediaRecorder.setVideoEncoder(recordingProfile.videoCodec);
        mediaRecorder.setVideoEncodingBitRate(recordingProfile.videoBitRate);
        if (enableAudio) mediaRecorder.setAudioSamplingRate(recordingProfile.audioSampleRate);
        mediaRecorder.setVideoFrameRate(recordingProfile.videoFrameRate);
        mediaRecorder.setVideoSize(recordingProfile.videoFrameWidth, recordingProfile.videoFrameHeight);
        mediaRecorder.setOutputFile(outputFilePath);
        mediaRecorder.setOrientationHint(getMediaOrientation());

        mediaRecorder.prepare();
    }

    @SuppressLint("MissingPermission")
    public void open(@NonNull final Result result) {
        camera = Camera.open(cameraId);
        cameraParameters = camera.getParameters();
        try {
            startPreview();
        } catch (CameraException e) {
            e.printStackTrace();
        }
        mPreviewSizes.clear();
        for (Camera.Size size : cameraParameters.getSupportedPreviewSizes()) {
            mPreviewSizes.add(new Size(size.width, size.height));
        }
        // Supported picture sizes;
        mPictureSizes.clear();
        for (Camera.Size size : cameraParameters.getSupportedPictureSizes()) {
            mPictureSizes.add(new Size(size.width, size.height));
        }
        Size previewSize = mPreviewSizes.sizes(DEFAULT_ASPECT_RATIO).last();
        Size pictureSize = mPictureSizes.sizes(DEFAULT_ASPECT_RATIO).last();
        cameraParameters.setPreviewSize(previewSize.getWidth(), previewSize.getHeight());
        cameraParameters.setPictureSize(pictureSize.getWidth(), pictureSize.getHeight());
        cameraParameters.setRotation(0);
        camera.setDisplayOrientation(90);
        camera.setParameters(cameraParameters);

        Map<String, Object> reply = new HashMap<>();
        reply.put("textureId", flutterTexture.id());
        reply.put("previewWidth", previewSize.getWidth());
        reply.put("previewHeight", previewSize.getHeight());
        result.success(reply);
    }

    private void writeToFile(ByteBuffer buffer, File file) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            while (0 < buffer.remaining()) {
                outputStream.getChannel().write(buffer);
            }
        }
    }

    SurfaceTextureEntry getFlutterTexture() {
        return flutterTexture;
    }

    public void takePicture(String filePath, @NonNull final Result result) {
        if (!isPictureCaptureInProgress.getAndSet(true)) {
            camera.takePicture(null, null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    final File file = new File(filePath);
                    if (file.exists()) {
                        result.error(
                                "fileExists", "File at path '" + filePath + "' already exists. Cannot overwrite.", null);
                        return;
                    }
                    FileOutputStream fileOutputStream = null;
                    try {
                        Bitmap originalImage = BitmapFactory.decodeByteArray(data, 0, data.length);
                        Matrix matrix = new Matrix();
                        if (isFrontFacing) {
                            matrix.preScale(-1, 1);
                            matrix.postRotate(getMediaOrientation() - 180);
                        } else {
                            matrix.postRotate(getMediaOrientation());
                        }
                        Bitmap rotatedBitmap = Bitmap.createBitmap(originalImage, 0, 0, originalImage.getWidth(), originalImage.getHeight(),
                                matrix, false);
                        fileOutputStream = new FileOutputStream(file);
                        rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
                        result.success(null);
                    } catch (IOException e) {
                        result.error("IOError", "Failed saving image", null);
                    } finally {
                        if (fileOutputStream != null) {
                            try {
                                fileOutputStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                        isPictureCaptureInProgress.set(false);
                        camera.cancelAutoFocus();
                        camera.startPreview();
                    }
                }
            });
        }
    }

    public void startVideoRecording(String filePath, Result result) {
        throw new UnsupportedOperationException("Video Recording not supported");
    }

    public void stopVideoRecording(@NonNull final Result result) {
        if (!recordingVideo) {
            result.success(null);
            return;
        }

        try {
            recordingVideo = false;
            mediaRecorder.stop();
            mediaRecorder.reset();
            startPreview();
            result.success(null);
        } catch (CameraException | IllegalStateException e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
        }
    }

    public void pauseVideoRecording(@NonNull final Result result) {
        if (!recordingVideo) {
            result.success(null);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder.pause();
            } else {
                result.error("videoRecordingFailed", "pauseVideoRecording requires Android API +24.", null);
                return;
            }
        } catch (IllegalStateException e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
            return;
        }

        result.success(null);
    }

    public void resumeVideoRecording(@NonNull final Result result) {
        if (!recordingVideo) {
            result.success(null);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder.resume();
            } else {
                result.error(
                        "videoRecordingFailed", "resumeVideoRecording requires Android API +24.", null);
                return;
            }
        } catch (IllegalStateException e) {
            result.error("videoRecordingFailed", e.getMessage(), null);
            return;
        }

        result.success(null);
    }

    public void startPreview() throws CameraException {
        try {
            camera.setPreviewCallbackWithBuffer(null);
            camera.setPreviewTexture(flutterTexture.surfaceTexture());
            camera.startPreview();
        } catch (RuntimeException | IOException e) {
            throw new CameraException(e);
        }
    }

    public void startPreviewWithImageStream(EventChannel imageStreamChannel)
            throws CameraException {
        try {
            imageStreamChannel.setStreamHandler(
                    new EventChannel.StreamHandler() {
                        @Override
                        public void onListen(Object o, EventChannel.EventSink imageStreamSink) {
                            Camera.Size size = camera.getParameters().getPreviewSize();
                            camera.setPreviewCallbackWithBuffer((data, camera) -> {
                                Camera.Size previewSize = camera.getParameters().getPreviewSize();
                                List<Map<String, Object>> planes = new ArrayList<>();
                                Map<String, Object> planeBuffer = new HashMap<>();
                                planeBuffer.put("bytesPerRow", previewSize.height);
                                planeBuffer.put("bytesPerPixel", ImageFormat.getBitsPerPixel(camera.getParameters().getPreviewFormat()));
                                planeBuffer.put("bytes", data);

                                planes.add(planeBuffer);

                                Map<String, Object> imageBuffer = new HashMap<>();
                                imageBuffer.put("width", previewSize.width);
                                imageBuffer.put("height", previewSize.height);
                                imageBuffer.put("format", camera.getParameters().getPreviewFormat());
                                imageBuffer.put("planes", planes);

                                imageStreamSink.success(imageBuffer);
                                camera.addCallbackBuffer(new byte[getYUVByteSize(size.height, size.width)]);
                            });
                            camera.addCallbackBuffer(new byte[getYUVByteSize(size.height, size.width)]);
                        }

                        @Override
                        public void onCancel(Object o) {
                            camera.setPreviewCallbackWithBuffer(null);
                        }
                    });
            camera.startPreview();
        } catch (RuntimeException e) {
            throw new CameraException(e);
        }
    }

    public void close() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();
            mediaRecorder.release();
            mediaRecorder = null;
        }
        camera.stopPreview();
    }

    public void dispose() {
        close();
        flutterTexture.release();
        orientationEventListener.disable();
    }

    private int getMediaOrientation() {
        final int sensorOrientationOffset =
                (currentOrientation == ORIENTATION_UNKNOWN)
                        ? 0
                        : (isFrontFacing) ? -currentOrientation : currentOrientation;
        return (sensorOrientationOffset + sensorOrientation + 360) % 360;
    }

    private int getYUVByteSize(int width, int height){
        int ySize = width * height;
        int uvSize = (width + 1) / 2 * ((height + 1) / 2) * 2;
        return ySize + uvSize;
    }
}
