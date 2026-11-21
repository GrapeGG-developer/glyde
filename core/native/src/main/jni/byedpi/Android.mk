LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := ciadpi
LOCAL_SRC_FILES := packets.c main.c conev.c proxy.c desync.c mpool.c extend.c
LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_CFLAGS := -std=c99 -O2 -D_DEFAULT_SOURCE -Wno-unused -Wno-unused-parameter
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384
include $(BUILD_EXECUTABLE)
