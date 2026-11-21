APP_OPTIM := release
APP_PLATFORM := android-29
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
APP_CFLAGS := -O3 -DPKGNAME=gpr/greentune/unblock/core -DCLSNAME=TProxyService
APP_LDFLAGS := -Wl,-z,max-page-size=16384
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true
NDK_TOOLCHAIN_VERSION := clang
