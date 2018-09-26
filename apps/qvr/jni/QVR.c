#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <time.h>
#include <unistd.h>
#include <android/log.h>
#include <android/native_window_jni.h>	// for native window JNI
#include <android/input.h>

#include <qtypes.h>
#include <quakedef.h>
#include <menu.h>
#include <cvar.h>

//All the functionality we link to in the DarkPlaces Engine implementation
extern void QC_BeginFrame();
extern void QC_DrawFrame(int eye, int x, int y);
extern void QC_EndFrame();
extern void QC_GetAudio();
extern void QC_KeyEvent(int state,int key,int character);
extern void QC_MoveEvent(float yaw, float pitch, float roll);
extern void QC_SetCallbacks(void *init_audio, void *write_audio);
extern void QC_SetResolution(int width, int height);
extern void QC_Analog(int enable,float x,float y);
extern void QC_MotionEvent(float delta, float dx, float dy);
extern int main (int argc, char **argv);

extern qboolean vrMode;
extern int bigScreen;
extern int gameAssetsDownloadStatus;

extern cvar_t cl_autocentreoffset;
extern cvar_t v_eyebufferresolution;

static JavaVM *jVM;
static jobject audioBuffer=0;
static jobject audioCallbackObj=0;

jmethodID android_initAudio;
jmethodID android_writeAudio;
jmethodID android_pauseAudio;
jmethodID android_resumeAudio;
jmethodID android_terminateAudio;

static jobject quakeCallbackObj=0;
jmethodID android_BigScreenMode;
jmethodID android_SwitchStereoMode;
jmethodID android_ControllerStrafeMode;
jmethodID android_Exit;

void jni_initAudio(void *buffer, int size)
{
    JNIEnv *env;
    jobject tmp;
    (*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4);
    tmp = (*env)->NewDirectByteBuffer(env, buffer, size);
    audioBuffer = (jobject)(*env)->NewGlobalRef(env, tmp);
    return (*env)->CallVoidMethod(env, audioCallbackObj, android_initAudio, size);
}

void jni_writeAudio(int offset, int length)
{
	if (audioBuffer==0) return;
    JNIEnv *env;
    if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
    {
    	(*jVM)->AttachCurrentThread(jVM,&env, NULL);
    }
    (*env)->CallVoidMethod(env, audioCallbackObj, android_writeAudio, audioBuffer, offset, length);
}

void jni_pauseAudio()
{
	if (audioBuffer==0) return;
    JNIEnv *env;
    if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
    {
    	(*jVM)->AttachCurrentThread(jVM,&env, NULL);
    }
    (*env)->CallVoidMethod(env, audioCallbackObj, android_pauseAudio);
}

void jni_resumeAudio()
{
	if (audioBuffer==0) return;
    JNIEnv *env;
    if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
    {
    	(*jVM)->AttachCurrentThread(jVM,&env, NULL);
    }
    (*env)->CallVoidMethod(env, audioCallbackObj, android_resumeAudio);
}

void jni_terminateAudio()
{
	if (audioBuffer==0) return;
    JNIEnv *env;
    if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
    {
    	(*jVM)->AttachCurrentThread(jVM,&env, NULL);
    }
    (*env)->CallVoidMethod(env, audioCallbackObj, android_terminateAudio);
}


void jni_BigScreenMode(int mode)
{
	JNIEnv *env;
	if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
	{
		(*jVM)->AttachCurrentThread(jVM,&env, NULL);
	}
	(*env)->CallVoidMethod(env, quakeCallbackObj, android_BigScreenMode, mode);
}


void jni_SwitchStereoMode(int mode)
{
	JNIEnv *env;
	if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
	{
		(*jVM)->AttachCurrentThread(jVM,&env, NULL);
	}
	(*env)->CallVoidMethod(env, quakeCallbackObj, android_SwitchStereoMode, mode);
}

void jni_ControllerStrafeMode(int mode)
{
	JNIEnv *env;
	if (((*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4))<0)
	{
		(*jVM)->AttachCurrentThread(jVM,&env, NULL);
	}
	(*env)->CallVoidMethod(env, quakeCallbackObj, android_ControllerStrafeMode, mode);
}

void jni_Exit()
{
	JNIEnv *env;
	jobject tmp;
	(*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4);
	(*env)->CallVoidMethod(env, quakeCallbackObj, android_Exit);
}

//Retain the folder we are using
char *strGameFolder = NULL;

//Timing stuff for joypad control
static long oldtime=0;
long delta=0;
float last_joystick_x=0;
float last_joystick_y=0;

int curtime;
int Sys_Milliseconds (void)
{
	struct timeval tp;
	struct timezone tzp;
	static int		secbase;

	gettimeofday(&tp, &tzp);

	if (!secbase)
	{
		secbase = tp.tv_sec;
		return tp.tv_usec/1000;
	}

	curtime = (tp.tv_sec - secbase)*1000 + tp.tv_usec/1000;

	return curtime;
}

int returnvalue = -1;
void QC_exit(int exitCode)
{
	returnvalue = exitCode;
	Host_Shutdown();
	jni_Exit();
}

vec3_t hmdorientation;
extern float gunangles[3];

int JNI_OnLoad(JavaVM* vm, void* reserved)
{
    JNIEnv *env;
    jVM = vm;
    if((*vm)->GetEnv(vm, (void**) &env, JNI_VERSION_1_4) != JNI_OK)
    {
		return -1;
    }

    return JNI_VERSION_1_4;
}

static void UnEscapeQuotes( char *arg )
{
	char *last = NULL;
	while( *arg ) {
		if( *arg == '"' && *last == '\\' ) {
			char *c_curr = arg;
			char *c_last = last;
			while( *c_curr ) {
				*c_last = *c_curr;
				c_last = c_curr;
				c_curr++;
			}
			*c_last = '\0';
		}
		last = arg;
		arg++;
	}
}

static int ParseCommandLine(char *cmdline, char **argv)
{
	char *bufp;
	char *lastp = NULL;
	int argc, last_argc;
	argc = last_argc = 0;
	for ( bufp = cmdline; *bufp; ) {
		while ( isspace(*bufp) ) {
			++bufp;
		}
		if ( *bufp == '"' ) {
			++bufp;
			if ( *bufp ) {
				if ( argv ) {
					argv[argc] = bufp;
				}
				++argc;
			}
			while ( *bufp && ( *bufp != '"' || *lastp == '\\' ) ) {
				lastp = bufp;
				++bufp;
			}
		} else {
			if ( *bufp ) {
				if ( argv ) {
					argv[argc] = bufp;
				}
				++argc;
			}
			while ( *bufp && ! isspace(*bufp) ) {
				++bufp;
			}
		}
		if ( *bufp ) {
			if ( argv ) {
				*bufp = '\0';
			}
			++bufp;
		}
		if( argv && last_argc != argc ) {
			UnEscapeQuotes( argv[last_argc] );
		}
		last_argc = argc;
	}
	if ( argv ) {
		argv[argc] = NULL;
	}
	return(argc);
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_setResolution( JNIEnv * env, jobject obj, int width, int height )
{
	QC_SetResolution(width, height);
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_initialise( JNIEnv * env, jobject obj, jstring gameFolder, jstring commandLineParams )
{
	static qboolean quake_initialised = false;
	if (!quake_initialised)
	{
		jboolean iscopy;
		const char * folder = (*env)->GetStringUTFChars(env, gameFolder, &iscopy);
		chdir(folder);
		strGameFolder = strdup(folder);
		(*env)->ReleaseStringUTFChars(env, gameFolder, folder);

		const char *arg = (*env)->GetStringUTFChars(env, commandLineParams, &iscopy);

		char *cmdLine = NULL;
		if (arg && strlen(arg))
		{
			cmdLine = strdup(arg);
		}

		(*env)->ReleaseStringUTFChars(env, commandLineParams, arg);

		if (cmdLine)
		{
			char **argv;
			int argc=0;
			argv = malloc(sizeof(char*) * 255);
			argc = ParseCommandLine(strdup(cmdLine), argv);
			main(argc, argv);
		}
		else
		{
			int argc =1; char *argv[] = { "quake" };
			main(argc, argv);
		}

		//Start game with credits active
		MR_ToggleMenu(1);
		quake_initialised = true;
	}
}

#define YAW 1
#define PITCH 0
#define ROLL 2

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_onNewFrame( JNIEnv * env, jobject obj,
																		 float pitch, float yaw, float roll,
																		 float gun_pitch, float gun_yaw, float gun_roll)
{
	long t=Sys_Milliseconds();
	delta=t-oldtime;
	oldtime=t;
	if (delta>1000)
	delta=1000;

	QC_MotionEvent(delta, last_joystick_x, last_joystick_y);

	//Save orientation
	if (headtracking)
	{
		hmdorientation[YAW] =	yaw;
		hmdorientation[PITCH] =	pitch;
		hmdorientation[ROLL] =	roll;
	}
	else
	{
		hmdorientation[YAW] =	0;
		hmdorientation[PITCH] =	0;
		hmdorientation[ROLL] =	0;

	}

	gunangles[PITCH] = gun_pitch;
	gunangles[YAW] = gun_yaw;
	gunangles[ROLL] = gun_roll;

	//Set move information
	QC_MoveEvent(hmdorientation[YAW], hmdorientation[PITCH], hmdorientation[ROLL]);

	//Set everything up
	QC_BeginFrame();
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_onDrawEye( JNIEnv * env, jobject obj, int eye, int x, int y )
{
	QC_DrawFrame(eye, x, y);

	//const GLenum depthAttachment[1] = { GL_DEPTH_ATTACHMENT };
	//glInvalidateFramebuffer( GL_FRAMEBUFFER, 1, depthAttachment );
	//glFlush();
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_onFinishFrame( JNIEnv * env, jobject obj )
{
	QC_EndFrame();
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_onBigScreenMode( JNIEnv * env, jobject obj, int mode )
{
	bigScreen = mode;
}

JNIEXPORT int JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_getCentreOffset( JNIEnv * env, jobject obj )
{
	return cl_autocentreoffset.integer;
}

JNIEXPORT int JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_getEyeBufferResolution( JNIEnv * env, jobject obj )
{
	return v_eyebufferresolution.integer;
}


JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_setCentreOffset( JNIEnv * env, jobject obj, int offset )
{
	//This is called only by the calculator, so only set it if it is not already set (i.e. by user or a previous run)
	if (cl_autocentreoffset.integer == 0)
		Cvar_SetValueQuick (&cl_autocentreoffset, offset);
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_onKeyEvent( JNIEnv * env, jobject obj, int keyCode, int action, int character )
{
	//Dispatch to quake
	QC_KeyEvent(action == AKEY_EVENT_ACTION_DOWN ? 1 : 0, keyCode, character);
}

#define SOURCE_GAMEPAD 	0x00000401
#define SOURCE_JOYSTICK 0x01000010
JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_onTouchEvent( JNIEnv * env, jobject obj, int source, int action, float x, float y )
{
	if (source == SOURCE_JOYSTICK || source == SOURCE_GAMEPAD)
		QC_Analog(true, x, y);
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_onMotionEvent( JNIEnv * env, jobject obj, int source, int action, float x, float y )
{
	if (source == SOURCE_JOYSTICK || source == SOURCE_GAMEPAD)
	{
		last_joystick_x=x;
		last_joystick_y=y;
	}
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_setCallbackObjects(JNIEnv *env, jobject obj, jobject obj1, jobject obj2)
{
    jclass audioCallbackClass;

    (*jVM)->GetEnv(jVM, (void**) &env, JNI_VERSION_1_4);

    audioCallbackObj = (jobject)(*env)->NewGlobalRef(env, obj1);
    audioCallbackClass = (*env)->GetObjectClass(env, audioCallbackObj);

    android_initAudio = (*env)->GetMethodID(env,audioCallbackClass,"initAudio","(I)V");
    android_writeAudio = (*env)->GetMethodID(env,audioCallbackClass,"writeAudio","(Ljava/nio/ByteBuffer;II)V");
    android_pauseAudio = (*env)->GetMethodID(env,audioCallbackClass,"pauseAudio","()V");
    android_resumeAudio = (*env)->GetMethodID(env,audioCallbackClass,"resumeAudio","()V");
    android_terminateAudio = (*env)->GetMethodID(env,audioCallbackClass,"terminateAudio","()V");


	jclass quakeCallbackClass;

	quakeCallbackObj = (jobject)(*env)->NewGlobalRef(env, obj2);
	quakeCallbackClass = (*env)->GetObjectClass(env, quakeCallbackObj);

	android_BigScreenMode = (*env)->GetMethodID(env,quakeCallbackClass,"BigScreenMode","(I)V");
	android_SwitchStereoMode = (*env)->GetMethodID(env,quakeCallbackClass,"SwitchStereoMode","(I)V");
	android_ControllerStrafeMode = (*env)->GetMethodID(env,quakeCallbackClass,"ControllerStrafeMode","(I)V");
	android_Exit = (*env)->GetMethodID(env,quakeCallbackClass,"Exit","()V");
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_requestAudioData(JNIEnv *env, jclass c, jlong handle)
{
	QC_GetAudio();
}

JNIEXPORT void JNICALL Java_com_drbeef_qvrdaydream_QVRJNILib_setDownloadStatus( JNIEnv * env, jobject obj, int status )
{
	gameAssetsDownloadStatus = status;
}