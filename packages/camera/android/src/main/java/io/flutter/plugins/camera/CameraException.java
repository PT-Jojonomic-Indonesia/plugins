package io.flutter.plugins.camera;

import android.util.AndroidException;

public class CameraException extends AndroidException {

    public CameraException() {
    }

    public CameraException(String name) {
        super(name);
    }

    public CameraException(String name, Throwable cause) {
        super(name, cause);
    }

    public CameraException(Exception cause) {
        super(cause);
    }

}
