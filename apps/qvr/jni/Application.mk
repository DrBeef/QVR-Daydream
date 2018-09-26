# Common build settings for all VR apps

# This needs to be defined to get the right header directories for egl / etc
APP_PLATFORM := android-26

APP_ABI := armeabi-v7a

# Statically link the GNU STL. This may not be safe for multi-so libraries but
# we don't know of any problems yet.
APP_STL := c++_shared

# Make sure every shared lib includes a .note.gnu.build-id header, for crash reporting
APP_LDFLAGS := -Wl,--build-id

NDK_TOOLCHAIN_VERSION := clang

# Define the directories for $(import-module, ...) to look in
ROOT_DIR := $(patsubst %/,%,$(dir $(lastword $(MAKEFILE_LIST))))
NDK_MODULE_PATH := 
