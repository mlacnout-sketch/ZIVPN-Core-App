#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <android/log.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <errno.h>

#define TAG "ZIVPN_JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_zivpn_core_BinaryManager_startTun2Socks(JNIEnv *env, jobject thiz, jint tun_fd, jstring path, jstring proxy) {
    const char *native_path = (*env)->GetStringUTFChars(env, path, 0);
    const char *native_proxy = (*env)->GetStringUTFChars(env, proxy, 0);

    LOGD("Forking to start tun2socks...");
    LOGD("Path: %s", native_path);
    LOGD("Proxy: %s", native_proxy);
    LOGD("FD: %d", tun_fd);

    pid_t pid = fork();

    if (pid < 0) {
        LOGE("Fork failed");
        return -1;
    }

    if (pid == 0) {
        // Child process
        char fd_str[16];
        sprintf(fd_str, "%d", tun_fd);

        // Prepare arguments
        // tun2socks --tun-fd <fd> --proxy <proxy>
        char *args[] = {
            (char *)native_path,
            "--tun-fd",
            fd_str,
            "--proxy",
            (char *)native_proxy,
            NULL
        };

        // Important: Android might have set FD_CLOEXEC on the TUN FD.
        // We must clear it to ensure it stays open across exec.
        int flags = fcntl(tun_fd, F_GETFD);
        if (flags != -1) {
             fcntl(tun_fd, F_SETFD, flags & ~FD_CLOEXEC);
        }

        execvp(native_path, args);

        // If we get here, exec failed
        LOGE("Exec failed: %s", strerror(errno));
        exit(1);
    }

    // Parent process
    (*env)->ReleaseStringUTFChars(env, path, native_path);
    (*env)->ReleaseStringUTFChars(env, proxy, native_proxy);

    return (jint)pid;
}
