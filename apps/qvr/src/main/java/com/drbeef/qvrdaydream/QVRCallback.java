package com.drbeef.qvrdaydream;


public interface QVRCallback {

    void BigScreenMode(int mode);
    void SwitchStereoMode(int stereo_mode);
    void ControllerStrafeMode(int strafe);
    void Exit();
}
