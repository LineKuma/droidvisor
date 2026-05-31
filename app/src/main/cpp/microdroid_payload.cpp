#include <android-base/logging.h>
#include <sys/socket.h>
#include <linux/vm_sockets.h>
#include <unistd.h>
#include <cstdlib>
#include <cstring>
#include <thread>
#include <vector>
#include <string>
#include <sstream>
#include <sys/wait.h>
#include <signal.h>

extern "C" void AVmPayload_notifyPayloadReady();
extern "C" int AVmPayload_main();

static int startVsockServer(int port);
static void handleClient(int clientFd);
static std::string executeCommand(const std::string& cmd);

extern "C" int AVmPayload_main() {
    LOG(INFO) << "DroidVisor payload started";

    std::thread dockerThread([]() {
        startVsockServer(2375);
    });
    dockerThread.detach();

    std::thread ttyThread([]() {
        startVsockServer(22);
    });
    ttyThread.detach();

    AVmPayload_notifyPayloadReady();
    LOG(INFO) << "DroidVisor payload ready";

    while (true) {
        sleep(60);
    }

    return 0;
}

static int startVsockServer(int port) {
    int serverFd = socket(AF_VSOCK, SOCK_STREAM, 0);
    if (serverFd < 0) {
        LOG(ERROR) << "Failed to create vsock socket on port " << port;
        return -1;
    }

    struct sockaddr_vm addr = {};
    addr.svm_family = AF_VSOCK;
    addr.svm_port = port;
    addr.svm_cid = VMADDR_CID_ANY;

    if (bind(serverFd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOG(ERROR) << "Failed to bind vsock on port " << port;
        close(serverFd);
        return -1;
    }

    if (listen(serverFd, 4) < 0) {
        LOG(ERROR) << "Failed to listen on vsock port " << port;
        close(serverFd);
        return -1;
    }

    LOG(INFO) << "Vsock server listening on port " << port;

    while (true) {
        int clientFd = accept(serverFd, nullptr, nullptr);
        if (clientFd < 0) continue;
        std::thread(handleClient, clientFd).detach();
    }

    close(serverFd);
    return 0;
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

static std::string executeCommand(const std::string& cmd) {
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
