package com.drbeef.qvrdaydream;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import android.support.v4.content.ContextCompat;

import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;
import com.google.vr.sdk.controller.Controller;
import com.google.vr.sdk.controller.ControllerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.IntBuffer;

import javax.microedition.khronos.egl.EGLConfig;


public class QVRActivity
        extends GvrActivity
        implements GvrView.StereoRenderer, QVRCallback
{
    private static final String TAG = "QVR-Daydream";

    private int permissionCount = 0;
    private static final int READ_EXTERNAL_STORAGE_PERMISSION_ID = 1;
    private static final int WRITE_EXTERNAL_STORAGE_PERMISSION_ID = 2;

    public enum AppState {
        INIT,
        NOGAMEDATA,
        DOWNLOADING,
        SPLASHSCREEN,
        QVRNOTINITIALISED,
        QVRINITIALISED
    }

    private AppState m_AppState = AppState.INIT;

	private OpenGL openGL = null;

    private int[] currentFBO = new int[1];
    private int fboEyeResolution = 0;
    //This is set when the user opts to use a different resolution to the one picked as the default
    private int desiredEyeBufferResolution = -1;

    private Bitmap titleBmp = null;


    //Head orientation
    private float[] eulerAngles = new float[3];
    //Controller/weapon orientation
    private float[] controllerAngles = new float[3];

    private int mStrafeMode = 1;

    private int MONO = 0;
    private int STEREO = 1;

    private int mStereoMode = STEREO;
    private int eyeID = 0;

    public static final int K_TAB = 9;
    public static final int K_ENTER = 13;
    public static final int K_ESCAPE = 27;
    public static final int K_SPACE	= 32;
    public static final int K_BACKSPACE	= 127;
    public static final int K_UPARROW = 128;
    public static final int K_DOWNARROW = 129;
    public static final int K_LEFTARROW = 130;
    public static final int K_RIGHTARROW = 131;
    public static final int K_ALT = 132;
    public static final int K_CTRL = 133;
    public static final int K_SHIFT = 134;
    public static final int K_F1 = 135;
    public static final int K_F2 = 136;
    public static final int K_F3 = 137;
    public static final int K_F4 = 138;
    public static final int K_F5 = 139;
    public static final int K_F6 = 140;
    public static final int K_F7 = 141;
    public static final int K_F8 = 142;
    public static final int K_F9 = 143;
    public static final int K_F10 = 144;
    public static final int K_F11 = 145;
    public static final int K_F12 = 146;
    public static final int K_INS = 147;
    public static final int K_DEL = 148;
    public static final int K_PGDN = 149;
    public static final int K_PGUP = 150;
    public static final int K_HOME = 151;
    public static final int K_END = 152;
    public static final int K_PAUSE = 153;
    public static final int K_NUMLOCK = 154;
    public static final int K_CAPSLOCK = 155;
    public static final int K_SCROLLOCK = 156;
    public static final int K_MOUSE1 = 512;
    public static final int K_MOUSE2 = 513;
    public static final int K_MOUSE3 = 514;
    public static final int K_MWHEELUP = 515;
    public static final int K_MWHEELDOWN = 516;
    public static final int K_MOUSE4 = 517;
    public static final int K_MOUSE5 = 518;

    /**
     * 0 = no big screen (in game)
     * 1 = big screen whilst menu or console active
     */
    private int bigScreen = 1;

    //Don't allow the trigger to fire more than once per 200ms
    private long triggerTimeout = 0;
    private int SOURCE_JOYSTICK = 0x01000010;

    private float M_PI = 3.14159265358979323846f;
    public static AudioCallback mAudio;
    //Read these from a file and pass through
    String commandLineParams = new String("");

    private GvrView gvrView;

    private DownloadTask mDownloadTask = null;

    public static boolean mVRModeChanged = true;

    //Can't rebuild eye buffers until surface changed flag recorded
    public static boolean mSurfaceChanged = false;


    private int[] splashTexture = new int[1];
    private MediaPlayer mPlayer;

    // These two objects are the primary APIs for interacting with the Daydream controller.
    private ControllerManager controllerManager;
    private Controller controller;

    static {
        try {
            Log.i("JNI", "Trying to load libqvr.so");
            System.loadLibrary("qvr");
        } catch (UnsatisfiedLinkError ule) {
            Log.e("JNI", "WARNING: Could not load libqvr.so");
        }
    }

    public void copy_asset(String name, String folder) {
        File f = new File(folder + name);
        if (!f.exists()) {
            //Ensure we have an appropriate folder
            new File(folder).mkdirs();
            _copy_asset(name, folder + name);
        }
    }

    public void _copy_asset(String name_in, String name_out) {
        AssetManager assets = this.getAssets();

        try {
            InputStream in = assets.open(name_in);
            OutputStream out = new FileOutputStream(name_out);

            copy_stream(in, out);

            out.close();
            in.close();

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public static void copy_stream(InputStream in, OutputStream out)
            throws IOException {
        byte[] buf = new byte[512];
        while (true) {
            int count = in.read(buf);
            if (count <= 0)
                break;
            out.write(buf, 0, count);
        }
    }

    void DrawDownloadProgress(String title, String... progress)
    {
// Create an empty, mutable bitmap
        Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_4444);

// get a canvas to paint over the bitmap
        Canvas canvas = new Canvas(bitmap);
        bitmap.eraseColor(0);

        Paint paint = new Paint();
        paint.setTextSize(20);
        //paint.setTypeface(type);
        paint.setAntiAlias(true);
        paint.setARGB(0xff, 0xff, 0x20, 0x00);

        //Add title banner
        canvas.drawBitmap(titleBmp, null, new Rect(10, 10, 500, 214), paint);

// Draw the text
        canvas.drawText(title, 32, 248, paint);
        int y = 272;
        for (int c = 0; c < progress.length; c++) {
            canvas.drawText(progress[c], 32, y, paint);
            y += 30;
        }

        openGL.CopyBitmapToTexture(bitmap, openGL.fbo.ColorTexture[0]);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


        gvrView = new GvrView(this);
        gvrView.setEGLConfigChooser(5, 6, 5, 0, 16, 0);

        gvrView.setRenderer(this);
        gvrView.setTransitionViewEnabled(true);

        setContentView(gvrView);

		openGL = new OpenGL();
		openGL.onCreate();

        EventListener listener = new EventListener();
        controllerManager = new ControllerManager(this, listener);
        controller = controllerManager.getController();
        controller.setEventListener(listener);

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        checkPermissionsAndInitialize();
    }

    /** Initializes the Activity only if the permission has been granted. */
    private void checkPermissionsAndInitialize() {
        // Boilerplate for checking runtime permissions in Android.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                    QVRActivity.this,
                    new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    WRITE_EXTERNAL_STORAGE_PERMISSION_ID);
        }
        else
        {
            permissionCount++;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
        {
                 ActivityCompat.requestPermissions(
                        QVRActivity.this,
                        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                        READ_EXTERNAL_STORAGE_PERMISSION_ID);
        }
        else
        {
            permissionCount++;
        }

        if (permissionCount == 2) {
            // Permissions have already been granted.
            create();
        }
    }

    /** Handles the user accepting the permission. */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_ID) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                permissionCount++;
            }
            else
            {
                Exit();
            }
        }

        if (requestCode == WRITE_EXTERNAL_STORAGE_PERMISSION_ID) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                permissionCount++;
            }
            else
            {
                Exit();
            }
        }

        checkPermissionsAndInitialize();
    }

    public void create() {

        //At the very least ensure we have a directory containing a config file
        copy_asset("config.cfg", QVRConfig.GetFullWorkingFolder() + "id1/");
        copy_asset("commandline.txt", QVRConfig.GetFullWorkingFolder());

        //See if user is trying to use command line params
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(QVRConfig.GetFullWorkingFolder() + "commandline.txt"));
            String s;
            StringBuilder sb=new StringBuilder(0);
            while ((s=br.readLine())!=null)
                sb.append(s + " ");
            br.close();

            commandLineParams = new String(sb.toString());
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (commandLineParams.contains("-game"))
        {
            //No need to download, user is playing something else
        }
        else
        {
            File f = new File(QVRConfig.GetFullWorkingFolder() + "id1/pak0.pak");
            if (!f.exists()) {
                m_AppState = AppState.NOGAMEDATA;
            }
            else
            {
                //Ok to progress to splash
                m_AppState = AppState.SPLASHSCREEN;
            }
        }

        if (mAudio==null)
        {
            mAudio = new AudioCallback();
        }

        Log.d(TAG, "QVRJNILib.setCallbackObjects");
        QVRJNILib.setCallbackObjects(mAudio, this);

        Log.d(TAG, "controllerManager.start");
        controllerManager.start();
    }

    public void startDownload()
        {
        mDownloadTask = new DownloadTask();
        mDownloadTask.set_context(QVRActivity.this);
        mDownloadTask.execute();
    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height)
    {
        Log.d(TAG, "onSurfaceChanged width = " + width + "  height = " + height);
        mSurfaceChanged = true;
    }

    @Override
    public void onSurfaceCreated(EGLConfig config) {
         Log.i(TAG, "onSurfaceCreated");

		openGL.onSurfaceCreated(config);
		openGL.SetupUVCoords();

        //Start intro music
        mPlayer = MediaPlayer.create(this, R.raw.m010912339);
        mPlayer.start();

        //Load bitmap for splash screen
        splashTexture[0] = 0;
        GLES20.glGenTextures(1, splashTexture, 0);

        Bitmap bmp = null;
        try {
            AssetManager assets = this.getAssets();
            InputStream in = assets.open("splash.jpg");
            bmp = BitmapFactory.decodeStream(in);
            in.close();
            openGL.CopyBitmapToTexture(bmp, splashTexture[0]);

            in = assets.open("title.jpg");
            titleBmp = BitmapFactory.decodeStream(in);
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void BigScreenMode(int mode)
    {
        if (mode == -1)
            bigScreen = 1;
        else if (bigScreen != 2)
            bigScreen = mode;
    }

    public void SwitchStereoMode(int stereo_mode)
    {
        mStereoMode = stereo_mode;
    }

    public void ControllerStrafeMode(int strafe)
    {
        mStrafeMode = strafe;
    }

    int getDesiredfboEyeResolution(int viewportWidth) {

        desiredEyeBufferResolution = QVRJNILib.getEyeBufferResolution();
        if (desiredEyeBufferResolution != 0)
            return desiredEyeBufferResolution;

        //Select based on viewport width
        if (viewportWidth > 1024)
            desiredEyeBufferResolution = 1024;
        else if (viewportWidth > 512)
            desiredEyeBufferResolution = 512;
        else
            desiredEyeBufferResolution = 256;

        return desiredEyeBufferResolution;
    }

    @Override
    public void onNewFrame(HeadTransform headTransform) {
        if (m_AppState == AppState.DOWNLOADING &&
                !mDownloadTask.isDownloading) {

            //We've finished downloading - progress to splashscreen stage
            m_AppState = AppState.SPLASHSCREEN;
        }

        if (m_AppState == AppState.QVRINITIALISED) {
            headTransform.getEulerAngles(eulerAngles, 0);

            float gun_yaw = controllerAngles[0];
            if (gun_yaw > 180.0f)
			{
				gun_yaw -= 360.0f;
			}

            QVRJNILib.onNewFrame(-eulerAngles[0] / (M_PI / 180.0f), eulerAngles[1] / (M_PI / 180.0f), -eulerAngles[2] / (M_PI / 180.0f),
                    -controllerAngles[1], gun_yaw, -controllerAngles[2]);

            //Check to see if we should update the eye buffer resolution
            int checkRes = QVRJNILib.getEyeBufferResolution();
            if (checkRes != 0 && checkRes != desiredEyeBufferResolution)
                mVRModeChanged = true;
        }
    }

    @Override
    public void onDrawEye(Eye eye) {
        if (mVRModeChanged)
        {
            if (!mSurfaceChanged)
                return;

            Log.i(TAG, "mVRModeChanged");
            if (openGL.fbo.FrameBuffer[0] != 0)
                openGL.DestroyFBO(openGL.fbo);

            fboEyeResolution = getDesiredfboEyeResolution(eye.getViewport().width);
            openGL.CreateFBO(openGL.fbo, fboEyeResolution, fboEyeResolution);
            QVRJNILib.setResolution(fboEyeResolution, fboEyeResolution);

            openGL.SetupUVCoords();

            mVRModeChanged = false;
        }

        if (m_AppState == AppState.QVRNOTINITIALISED)
        {
            QVRJNILib.initialise(QVRConfig.GetFullWorkingFolder(), commandLineParams);
            m_AppState = AppState.QVRINITIALISED;
        }

        if (m_AppState != AppState.INIT) {

            //Record the curent fbo
            GLES20.glGetIntegerv(GLES20.GL_FRAMEBUFFER_BINDING, currentFBO, 0);

            if (m_AppState == AppState.SPLASHSCREEN ||
                    m_AppState == AppState.NOGAMEDATA ||
                    m_AppState == AppState.DOWNLOADING) {
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
                GLES20.glScissor(eye.getViewport().x, eye.getViewport().y,
                        eye.getViewport().width, eye.getViewport().height);
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                if (m_AppState == AppState.DOWNLOADING) {
                    DrawDownloadProgress("-- Downloading Shareware Assets --",
                            "",
                            "Please Wait...");
                }
                else if (m_AppState == AppState.NOGAMEDATA)
                {
                    DrawDownloadProgress("-- No game data found! --",
                            "",
                            "Click Daydream Trackpad to download shareware assets",
                            "Click App Button to Quit");
                }
            }
            else if (mStereoMode == STEREO ||
                    (mStereoMode == MONO && eye.getType() < 2)) {
                //Bind our special fbo
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, openGL.fbo.FrameBuffer[0]);
                GLES20.glEnable(GLES20.GL_DEPTH_TEST);
                GLES20.glDepthFunc(GLES20.GL_LEQUAL);
                GLES20.glEnable(GLES20.GL_SCISSOR_TEST);

                GLES20.glScissor(0, 0, fboEyeResolution, fboEyeResolution);

                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                //Decide which eye we are drawing
                if (mStereoMode == MONO)
                    eyeID = 0;
                else // mStereoMode == StereoMode.STEREO  -  Default behaviour for VR mode
                    eyeID = eye.getType() - 1;

                //We only draw from QVR if we are running
                if (m_AppState == AppState.QVRINITIALISED)
                    QVRJNILib.onDrawEye(eyeID, 0, 0);

                //Finished rendering to our frame buffer, now draw this to the target framebuffer
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, currentFBO[0]);
            }

            GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

            GLES20.glViewport(eye.getViewport().x,
                        eye.getViewport().y,
                        eye.getViewport().width,
                        eye.getViewport().height);

            GLES20.glUseProgram(openGL.sp_Image);

            // Build the ModelView and ModelViewProjection matrices
            // for calculating screen position.
            float[] perspective = eye.getPerspective(0.1f, 100.0f);
			float modelScreen[] = new float[16];
			Matrix.setIdentityM(modelScreen, 0);

            if (bigScreen != 0)
            {
                // Apply the eye transformation to the camera.
                Matrix.multiplyMM(openGL.view, 0, eye.getEyeView(), 0, openGL.camera, 0);

                // Set the position of the screen
                if (m_AppState == AppState.SPLASHSCREEN ||
                        m_AppState == AppState.NOGAMEDATA ||
                        m_AppState == AppState.DOWNLOADING) {
                    // Object first appears directly in front of user.
                    Matrix.translateM(modelScreen, 0, 0, 0, -openGL.splashScreenDistance);
                    Matrix.scaleM(modelScreen, 0, openGL.splashScreenScale, openGL.splashScreenScale, 1.0f);

                    if (m_AppState == AppState.SPLASHSCREEN) {
						float mAngle = 90.0f - (180.0f * (float) ((System.currentTimeMillis() % 3000) / 3000.0f));
						Matrix.rotateM(modelScreen, 0, mAngle, 0.0f, 1.0f, 0.0f);
					}

                    Matrix.multiplyMM(openGL.modelView, 0, openGL.view, 0, modelScreen, 0);
                    Matrix.multiplyMM(openGL.modelViewProjection, 0, perspective, 0, openGL.modelView, 0);
                    GLES20.glVertexAttribPointer(openGL.positionParam, 3, GLES20.GL_FLOAT, false, 0, openGL.splashScreenVertices);
                } else {
                    // Object first appears directly in front of user.
                    Matrix.translateM(modelScreen, 0, 0, 0, -openGL.bigScreenDistance);
                    Matrix.scaleM(modelScreen, 0, openGL.bigScreenScale*1.2f, openGL.bigScreenScale, 1.0f);

                    Matrix.multiplyMM(openGL.modelView, 0, openGL.view, 0, modelScreen, 0);
                    Matrix.multiplyMM(openGL.modelViewProjection, 0, perspective, 0, openGL.modelView, 0);
                    GLES20.glVertexAttribPointer(openGL.positionParam, 3, GLES20.GL_FLOAT, false, 0, openGL.screenVertices);
                }
            }
            else
            {
                //Don't use head/eye transformation
                Matrix.translateM(modelScreen, 0, 0, 0, -openGL.gameScreenDistance);
                Matrix.scaleM(modelScreen, 0, openGL.gameScreenScale, openGL.gameScreenScale, 1.0f);

                // Build the ModelView and ModelViewProjection matrices
                // for calculating screen position.
                Matrix.multiplyMM(openGL.modelView, 0, openGL.camera, 0, modelScreen, 0);
                Matrix.multiplyMM(openGL.modelViewProjection, 0, perspective, 0, openGL.modelView, 0);
                GLES20.glVertexAttribPointer(openGL.positionParam, 3, GLES20.GL_FLOAT, false, 0, openGL.screenVertices);
            }

            // Prepare the texturecoordinates
            GLES20.glVertexAttribPointer(openGL.texCoordParam, 2, GLES20.GL_FLOAT, false, 0, openGL.uvBuffer);

            // Apply the projection and view transformation
            GLES20.glUniformMatrix4fv(openGL.modelViewProjectionParam, 1, false, openGL.modelViewProjection, 0);

            // Bind texture to fbo's color texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            IntBuffer activeTex0 = IntBuffer.allocate(2);
            GLES20.glGetIntegerv(GLES20.GL_TEXTURE_BINDING_2D, activeTex0);

            switch (m_AppState)
            {
                case SPLASHSCREEN:
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, splashTexture[0]);
                    break;
                default:
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, openGL.fbo.ColorTexture[0]);
                    break;
            }

            // Set the sampler texture unit to our fbo's color texture
            GLES20.glUniform1i(openGL.samplerParam, 0);

            // Draw the triangles
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, openGL.listBuffer);

            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR)
                Log.d(TAG, "GLES20 Error = " + error);

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, activeTex0.get(0));
        }
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
        if (m_AppState == AppState.QVRINITIALISED) {
             QVRJNILib.onFinishFrame();
        }
    }

    public int getCharacter(int keyCode, KeyEvent event)
    {
        if (keyCode==KeyEvent.KEYCODE_DEL) return '\b';
        return event.getUnicodeChar();
    }

    private void dismissSplashScreen()
    {
        if (m_AppState == AppState.SPLASHSCREEN) {
            mPlayer.stop();
            mPlayer.release();
            m_AppState = AppState.QVRNOTINITIALISED;
        }
    }

    @Override public boolean dispatchKeyEvent( KeyEvent event )
    {
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        int character;

        if ( action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP )
        {
            return super.dispatchKeyEvent( event );
        }

        //Convert to QVR keys
        character = getCharacter(keyCode, event);
        int qKeyCode = convertKeyCode(keyCode, event);

        //Don't hijack all keys (volume etc)
        if (qKeyCode != -1)
            keyCode = qKeyCode;

        if (m_AppState == AppState.QVRINITIALISED) {
            QVRJNILib.onKeyEvent(keyCode, action, character);
        }

        return true;
    }

    private static float getCenteredAxis(MotionEvent event,
                                         int axis) {
        final InputDevice.MotionRange range = event.getDevice().getMotionRange(axis, event.getSource());
        if (range != null) {
            final float flat = range.getFlat();
            final float value = event.getAxisValue(axis);
            if (Math.abs(value) > flat) {
                return value;
            }
        }
        return 0;
    }


    //Save the game pad type once known:
    // 1 - Generic BT gamepad
    // 2 - Samsung gamepad that uses different axes for right stick
    int gamepadType = 0;

    int lTrigAction = KeyEvent.ACTION_UP;
    int rTrigAction = KeyEvent.ACTION_UP;
    int nextWeapon  = KeyEvent.ACTION_UP;
    int prevWeapon  = KeyEvent.ACTION_UP;

    //Daydream Controller buttons
    int startButtonAction = KeyEvent.ACTION_UP;
    boolean bIsTouchingState = false;

    private class EventListener extends Controller.EventListener
            implements ControllerManager.EventListener {

        @Override
        public void onApiStatusChanged(int state) {
        }

        @Override
        public void onConnectionStateChanged(int state) {
        }

        @Override
        public void onRecentered() {
        }

        private float m_TouchX = Float.MAX_VALUE;
        private float m_TouchY = Float.MAX_VALUE;

        @Override
        public void onUpdate() {
            controller.update();

            {
                controller.orientation.toYawPitchRollDegrees(controllerAngles);

                //Log.d(TAG, "Controller:  Yaw: " + controllerAngles[0]);
                //Log.d(TAG, "HMD:  Yaw: " + (eulerAngles[1] / (M_PI / 180.0f)));


                float controllerRelativeYaw = (eulerAngles[1] / (M_PI / 180.0f)) - controllerAngles[0];
                float touchX = controller.isTouching ? (controller.touch.x - 0.5f) * 2.0f : 0.0f;
                float touchY = controller.isTouching ? (controller.touch.y - 0.5f) * 2.0f : 0.0f;

                if (bigScreen != 0)
                {
                    if (controller.clickButtonState ||
                            (controller.isTouching && m_TouchX == Float.MAX_VALUE))
                    {
                        m_TouchX = touchX;
                        m_TouchY = touchY;
                    }
                    else if (!controller.isTouching && m_TouchX != Float.MAX_VALUE)
                    {
                        m_TouchX = Float.MAX_VALUE;
                        m_TouchY = Float.MAX_VALUE;
                    }
                    else if (m_TouchX != Float.MAX_VALUE)
                    {
                        if ((touchX - m_TouchX) < -0.4f)
                        {
                            m_TouchX = touchX;
                            QVRJNILib.onKeyEvent('a', KeyEvent.ACTION_DOWN, 0);
                            QVRJNILib.onKeyEvent('a', KeyEvent.ACTION_UP, 0);
                        }
                        else if ((touchX - m_TouchX) > 0.4f)
                        {
                            m_TouchX = touchX;
                            QVRJNILib.onKeyEvent('d', KeyEvent.ACTION_DOWN, 0);
                            QVRJNILib.onKeyEvent('d', KeyEvent.ACTION_UP, 0);
                        }

                        if ((touchY - m_TouchY) < -0.5f)
                        {
                            m_TouchY = touchY;
                            QVRJNILib.onKeyEvent(K_UPARROW, KeyEvent.ACTION_DOWN, 0);
                            QVRJNILib.onKeyEvent(K_UPARROW, KeyEvent.ACTION_UP, 0);
                        }
                        else if ((touchY - m_TouchY) > 0.5f)
                        {
                            m_TouchY = touchY;
                            QVRJNILib.onKeyEvent(K_DOWNARROW, KeyEvent.ACTION_DOWN, 0);
                            QVRJNILib.onKeyEvent(K_DOWNARROW, KeyEvent.ACTION_UP, 0);
                        }
                    }

                    int newRTrig = controller.clickButtonState ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                    if (rTrigAction != newRTrig) {

                        if (m_AppState == AppState.NOGAMEDATA)
                        {
                            startDownload();
                            m_AppState = AppState.DOWNLOADING;
                        }

                        dismissSplashScreen(); // Dismiss if required

                        if (m_AppState == AppState.QVRINITIALISED) {
                            QVRJNILib.onKeyEvent(K_MOUSE1, newRTrig, 0);
                        }

                        rTrigAction = newRTrig;
                    }
                }
                else {
                    if (controller.isTouching || !bIsTouchingState) {

                        //Deadzones
                        if (touchY < 0.2f && touchY > -0.2f)
                        {
                            touchY = 0.0f;
                        }
                        if (touchX < 0.2f && touchX > -0.2f)
                        {
                            touchX = 0.0f;
                        }

                        if (mStrafeMode == 0) {
                            QVRJNILib.onTouchEvent(SOURCE_JOYSTICK, 0, (float) (Math.sin(Math.toRadians(controllerRelativeYaw)) * -touchY),
                                    (float) (Math.cos(Math.toRadians(controllerRelativeYaw)) * -touchY));
                        }
                        else
                        {
                            QVRJNILib.onTouchEvent(SOURCE_JOYSTICK, 0, touchX, -touchY);
                        }

                        bIsTouchingState = !controller.isTouching;
                    }

                    //Fire
                    int newRTrig = controller.clickButtonState ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                    if (rTrigAction != newRTrig) {
                        QVRJNILib.onKeyEvent(K_MOUSE1, newRTrig, 0);
                        rTrigAction = newRTrig;
                    }

                    //Jump
                    float pitch = controllerAngles[1];
                    int newLTrig = pitch > 40.0f ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                    if (lTrigAction != newLTrig)
                    {
                        QVRJNILib.onKeyEvent( K_SPACE, newLTrig, 0);
                        lTrigAction = newLTrig;
                    }

                    //Next Weapon
                    float roll = controllerAngles[2];
                    int newNextWeapon = roll > 90.0f ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                    if (nextWeapon != newNextWeapon)
                    {
                        QVRJNILib.onKeyEvent( convertKeyCode(KeyEvent.KEYCODE_BUTTON_Y, new KeyEvent(0, 0)), newNextWeapon, 0);
                        nextWeapon = newNextWeapon;
                    }

                    //Prev Weapon
                    int newPrevWeapon = roll < -90.0f ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                    if (prevWeapon != newPrevWeapon)
                    {
                        QVRJNILib.onKeyEvent( convertKeyCode(KeyEvent.KEYCODE_BUTTON_X, new KeyEvent(0, 0)), newPrevWeapon, 0);
                        prevWeapon = newPrevWeapon;
                    }
                }

                //Start
                int startButton = controller.appButtonState ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                if (startButtonAction != startButton)
                {
                    //If user pushes app button while we are showing the bo game data, then just quit
                    if (m_AppState == AppState.NOGAMEDATA)
                    {
                        Exit();
                    }

                    dismissSplashScreen();

                    if (m_AppState == AppState.QVRINITIALISED) {
                        QVRJNILib.onKeyEvent(K_ESCAPE, startButton, 0);
                        startButtonAction = startButton;
                    }
                }
            }
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        int source = event.getSource();
        int action = event.getAction();
        if ((source==InputDevice.SOURCE_JOYSTICK)||(event.getSource()==InputDevice.SOURCE_GAMEPAD))
        {
            if (event.getAction() == MotionEvent.ACTION_MOVE)
            {
                float x = getCenteredAxis(event, MotionEvent.AXIS_X);
                float y = -getCenteredAxis(event, MotionEvent.AXIS_Y);
                QVRJNILib.onTouchEvent( source, action, x, y );

                float z = getCenteredAxis(event, MotionEvent.AXIS_Z);
                float rz = -getCenteredAxis(event, MotionEvent.AXIS_RZ);
                //For the samsung game pad (uses different axes for the second stick)
                float rx = getCenteredAxis(event, MotionEvent.AXIS_RX);
                float ry = -getCenteredAxis(event, MotionEvent.AXIS_RY);

                //let's figure it out
                if (gamepadType == 0)
                {
                    if (z != 0.0f || rz != 0.0f)
                        gamepadType = 1;
                    else if (rx != 0.0f || ry != 0.0f)
                        gamepadType = 2;
                }

                switch (gamepadType)
                {
                    case 0:
                        break;
                    case 1:
                        QVRJNILib.onMotionEvent( source, action, z, rz );
                        break;
                    case 2:
                        QVRJNILib.onMotionEvent( source, action, rx, ry );
                        break;
                }

                //Fire weapon using shoulder trigger
                float axisRTrigger = max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                        event.getAxisValue(MotionEvent.AXIS_GAS));
                int newRTrig = axisRTrigger > 0.6 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                if (rTrigAction != newRTrig)
                {
                    QVRJNILib.onKeyEvent( K_MOUSE1, newRTrig, 0);
                    rTrigAction = newRTrig;
                }

                //Run using L shoulder
                float axisLTrigger = max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                        event.getAxisValue(MotionEvent.AXIS_BRAKE));
                int newLTrig = axisLTrigger > 0.6 ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
                if (lTrigAction != newLTrig)
                {
                    QVRJNILib.onKeyEvent( K_SHIFT, newLTrig, 0);
                    lTrigAction = newLTrig;
                }
            }
        }
        return false;
    }

    private float max(float axisValue, float axisValue2) {
        return (axisValue > axisValue2) ? axisValue : axisValue2;
    }

    public static int convertKeyCode(int keyCode, KeyEvent event)
    {
        switch(keyCode)
        {
            case KeyEvent.KEYCODE_FOCUS:
                return K_F1;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                return K_UPARROW;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                return K_DOWNARROW;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return 'a';
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return 'd';
            case KeyEvent.KEYCODE_DPAD_CENTER:
                return K_CTRL;
            case KeyEvent.KEYCODE_ENTER:
                return K_ENTER;
			case KeyEvent.KEYCODE_BACK:
				return K_ESCAPE;
            case KeyEvent.KEYCODE_APOSTROPHE:
                return K_ESCAPE;
            case KeyEvent.KEYCODE_DEL:
                return K_BACKSPACE;
            case KeyEvent.KEYCODE_ALT_LEFT:
            case KeyEvent.KEYCODE_ALT_RIGHT:
                return K_ALT;
            case KeyEvent.KEYCODE_SHIFT_LEFT:
            case KeyEvent.KEYCODE_SHIFT_RIGHT:
                return K_SHIFT;
            case KeyEvent.KEYCODE_CTRL_LEFT:
            case KeyEvent.KEYCODE_CTRL_RIGHT:
                return K_CTRL;
            case KeyEvent.KEYCODE_INSERT:
                return K_INS;
            case 122:
                return K_HOME;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                return K_DEL;
            case 123:
                return K_END;
            case KeyEvent.KEYCODE_ESCAPE:
                return K_ESCAPE;
            case KeyEvent.KEYCODE_TAB:
                return K_TAB;
            case KeyEvent.KEYCODE_F1:
                return K_F1;
            case KeyEvent.KEYCODE_F2:
                return K_F2;
            case KeyEvent.KEYCODE_F3:
                return K_F3;
            case KeyEvent.KEYCODE_F4:
                return K_F4;
            case KeyEvent.KEYCODE_F5:
                return K_F5;
            case KeyEvent.KEYCODE_F6:
                return K_F6;
            case KeyEvent.KEYCODE_F7:
                return K_F7;
            case KeyEvent.KEYCODE_F8:
                return K_F8;
            case KeyEvent.KEYCODE_F9:
                return K_F9;
            case KeyEvent.KEYCODE_F10:
                return K_F10;
            case KeyEvent.KEYCODE_F11:
                return K_F11;
            case KeyEvent.KEYCODE_F12:
                return K_F12;
            case KeyEvent.KEYCODE_CAPS_LOCK:
                return K_CAPSLOCK;
            case KeyEvent.KEYCODE_PAGE_DOWN:
                return K_PGDN;
            case KeyEvent.KEYCODE_PAGE_UP:
                return K_PGUP;
            case KeyEvent.KEYCODE_BUTTON_A:
                return K_ENTER;
            case KeyEvent.KEYCODE_BUTTON_B:
                return K_MOUSE1;
            case KeyEvent.KEYCODE_BUTTON_X:
                return '#'; //prev weapon, set in the config.txt as impulse 12
            case KeyEvent.KEYCODE_BUTTON_Y:
                return '/';//Next weapon, set in the config.txt as impulse 10
            //These buttons are not so popular
            case KeyEvent.KEYCODE_BUTTON_C:
                return 'a';//That's why here is a, nobody cares.
            case KeyEvent.KEYCODE_BUTTON_Z:
                return 'z';
            //--------------------------------
            case KeyEvent.KEYCODE_BUTTON_START:
                return K_ESCAPE;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return K_ENTER;
            case KeyEvent.KEYCODE_MENU:
                return K_ESCAPE;

            //Both shoulder buttons will "fire"
            case KeyEvent.KEYCODE_BUTTON_R1:
            case KeyEvent.KEYCODE_BUTTON_R2:
                return K_MOUSE1;

            //enables "run"
            case KeyEvent.KEYCODE_BUTTON_L1:
            case KeyEvent.KEYCODE_BUTTON_L2:
                return K_SHIFT;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                return -1;
        }
        int uchar = event.getUnicodeChar(0);
        if((uchar < 127)&&(uchar!=0))
            return uchar;
        return keyCode%95+32;//Magic
    }

    @Override
    public void Exit() {
        mAudio.terminateAudio();
        try {
            Thread.sleep(1000);
        }
        catch (InterruptedException ie){
        }
        System.exit(0);
    }


}
