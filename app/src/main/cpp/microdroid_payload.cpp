#include <android/log.h>
#include <sys/socket.h>
#include <linux/vm_sockets.h>
#include <unistd.h>

#ifndef AF_VSOCK
#define AF_VSOCK 40
#endif

#ifndef VMADDR_CID_ANY
#define VMADDR_CID_ANY 0xFFFFFFFF
#endif

#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include <thread>
#include <vector>
#include <string>
#include <sstream>
#include <sys/wait.h>
#include <signal.h>
#include <cerrno>

#define LOG_TAG "DroidVisorPayload"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef void (*NotifyPayloadReadyFunc)();

static NotifyPayloadReadyFunc getNotifyPayloadReady() {
    void* handle = dlopen("libmicrodroid_payload.so", RTLD_NOW);
    if (!handle) {
        LOGI("libmicrodroid_payload.so not available, running in non-Microdroid mode");
        return nullptr;
    }
    auto func = reinterpret_cast<NotifyPayloadReadyFunc>(dlsym(handle, "AVmPayload_notifyPayloadReady"));
    if (!func) {
        LOGI("AVmPayload_notifyPayloadReady symbol not found");
        dlclose(handle);
        return nullptr;
    }
    return func;
}

static int startVsockServer(int port);
static void handleClient(int clientFd);
static std::string executeCommand(const std::string& cmd);

extern "C" int AVmPayload_main() {
    LOGI("DroidVisor payload started");

    std::thread dockerThread([]() {
        startVsockServer(2375);
    });
    dockerThread.detach();

    std::thread ttyThread([]() {
        startVsockServer(22);
    });
    ttyThread.detach();

    auto notifyPayloadReady = getNotifyPayloadReady();
    if (notifyPayloadReady) {
        notifyPayloadReady();
        LOGI("DroidVisor payload ready (Microdroid)");
    } else {
        LOGI("DroidVisor payload ready (standalone)");
    }

    while (true) {
        sleep(60);
    }

    return 0;
}

static int startVsockServer(int port) {
    int serverFd = socket(AF_VSOCK, SOCK_STREAM, 0);
    if (serverFd < 0) {
        LOGE("Failed to create vsock socket on port %d", port);
        return -1;
    }

    struct sockaddr_vm addr = {};
    addr.svm_family = AF_VSOCK;
    addr.svm_port = port;
    addr.svm_cid = VMADDR_CID_ANY;

    if (bind(serverFd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOGE("Failed to bind vsock on port %d", port);
        close(serverFd);
        return -1;
    }

    if (listen(serverFd, 4) < 0) {
        LOGE("Failed to listen on vsock port %d", port);
        close(serverFd);
        return -1;
    }

    LOGI("Vsock server listening on port %d", port);

    while (true) {
        int clientFd = accept(serverFd, nullptr, nullptr);
        if (clientFd < 0) {
            LOGE("accept() failed on port %d: %s", port, strerror(errno));
            usleep(100000);
            continue;
        }
        std::thread(handleClient, clientFd).detach();
    }
}

static void handleClient(int clientFd) {
    char buffer[4096];
    while (true) {
        ssize_t n = read(clientFd, buffer, sizeof(buffer) - 1);
        if (n <= 0) break;
        buffer[n] = '\0';
        std::string result = executeCommand(std::string(buffer));
        write(clientFd, result.c_str(), result.size());
    }
    close(clientFd);
}

static const std::vector<std::string> ALLOWED_COMMANDS = {
    "ls", "cat", "echo", "whoami", "uname", "df", "free", "ps", "uptime",
    "date", "hostname", "id", "pwd", "env", "mount", "ifconfig", "ip"
};

static bool isCommandAllowed(const std::string& cmd) {
    static const std::string SHELL_META = ";|&$`\\\"'<>(){}!#~";
    if (cmd.find_first_of(SHELL_META) != std::string::npos) {
        return false;
    }
    std::string baseCmd = cmd.substr(0, cmd.find(' '));
    if (baseCmd.empty()) return false;
    size_t lastSlash = baseCmd.rfind('/');
    if (lastSlash != std::string::npos) {
        baseCmd = baseCmd.substr(lastSlash + 1);
    }
    for (const auto& allowed : ALLOWED_COMMANDS) {
        if (baseCmd == allowed) return true;
    }
    return false;
}

static std::string executeCommand(const std::string& cmd) {
    if (!isCommandAllowed(cmd)) {
        LOGE("Rejected disallowed command: %s", cmd.c_str());
        return "Error: command not allowed\n";
    }
    std::string result;
    FILE* pipe = popen(cmd.c_str(), "r");
    if (!pipe) return "Error: failed to execute command\n";
    char buffer[128];
    while (fgets(buffer, sizeof(buffer), pipe)) {
        result += buffer;
    }
    pclose(pipe);
    return result;
}
