package com.drbeef.qvrdaydream;

public class QVRJNILib {

    // Input
    public static native void onKeyEvent( int keyCode, int action, int character );
    public static native void onTouchEvent( int source, int action, float x, float y );
    public static native void onMotionEvent( int source, int action, float x, float y );

    //Rendering and lifecycle
    public static native void setResolution( int width, int height );
    public static native void initialise( String gameFolder, String commandLineParams );
    public static native void onNewFrame( float pitch, float yaw, float roll, float gun_pitch, float gun_yaw, float gun_roll );
    public static native void onDrawEye( int eye, int x, int y );
    public static native void onFinishFrame( );
    public static native void onBigScreenMode( int mode );
    public static native int  getCentreOffset( );
    public static native void setCentreOffset( int offset );
    public static native void setDownloadStatus( int status );
    public static native int  getEyeBufferResolution( );

    //Audio
    public static native void requestAudioData();
    public static native void setCallbackObjects(Object obj1, Object obj2);
}
