package com.drbeef.qvrdaydream;


import android.os.Environment;

import java.io.File;

public class QVRConfig {

    static public String GetSDCARD()
    {
        return Environment.getExternalStorageDirectory().getPath();
    }

    static public String GetWorkingFolder()
    {
        //Do we use old folder name?
        if (new File(GetSDCARD() + "/Q4C").exists())
        {
            return "Q4C/";
        }
        else
        {
            return "QVR/";
        }
    }

    static public String GetFullWorkingFolder()
    {
        return GetSDCARD() + "/" + GetWorkingFolder();
    }
}
