# Droidvisor Full Android SDK + AVD Environment
# Includes: JDK 17, Android SDK (cmdline-tools, platform-tools, emulator,
# platforms 34+35, build-tools, NDK, CMake), and a pre-created AVD.
FROM ubuntu:22.04

ARG DEBIAN_FRONTEND=noninteractive

WORKDIR /workspace

# ── OS packages ──────────────────────────────────────────────────────
# Emulator needs: libpulse0, libgl1, libx11-*, libnss3, libxcomposite1,
# libxcursor1, libxi6, libxtst6, libxrandr2, libxss1, libasound2
RUN apt-get update && apt-get install -y --no-install-recommends \
    wget \
    unzip \
    curl \
    git \
    openjdk-17-jdk-headless \
    ca-certificates \
    ca-certificates-java \
    libpulse0 \
    libgl1 \
    libnss3 \
    libx11-6 \
    libxcomposite1 \
    libxcursor1 \
    libxi6 \
    libxtst6 \
    libxrandr2 \
    libxss1 \
    libasound2 \
    libatk1.0-0 \
    libatk-bridge2.0-0 \
    libcups2 \
    libdrm2 \
    libgbm1 \
    libgtk-3-0 \
    libpango-1.0-0 \
    libcairo2 \
    libxrender1 \
    libfontconfig1 \
    libfreetype6 \
    jq \
    procps \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# ── Java ─────────────────────────────────────────────────────────────
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# ── Android SDK ──────────────────────────────────────────────────────
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_AVD_HOME=/root/.android/avd

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    cd ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    echo "2d2d50857e4eb553af5a6dc3ad507a17adf43d115264b1afc116f95c92e5e258  cmdline-tools.zip" | sha256sum -c - && \
    unzip -q cmdline-tools.zip && \
    mv cmdline-tools latest && \
    rm cmdline-tools.zip

ENV PATH=$PATH:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator

RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true

RUN sdkmanager \
    "platform-tools" \
    "emulator" \
    "platforms;android-34" \
    "platforms;android-35" \
    "build-tools;34.0.0" \
    "build-tools;35.0.0"

RUN for i in 1 2 3; do \
        if sdkmanager "ndk;26.1.10909125" "cmake;3.22.1"; then \
            echo "NDK + CMake installed successfully"; \
            break; \
        fi; \
        echo "Attempt $i failed, retrying..."; \
        rm -rf ${ANDROID_HOME}/ndk/26.1.10909125 ${ANDROID_HOME}/cmake/3.22.1 2>/dev/null || true; \
        sleep 5; \
    done

RUN sdkmanager "system-images;android-34;google_apis;x86_64"

# ── AVD setup ────────────────────────────────────────────────────────
RUN echo "no" | avdmanager create avd \
        --force \
        --name droidvisor_test \
        --package "system-images;android-34;google_apis;x86_64" \
        --device "pixel_6" \
        --abi "x86_64"

RUN AVD_INI="/root/.android/avd/droidvisor_test.avd/config.ini" && \
    if [ -f "$AVD_INI" ]; then \
        sed -i 's/hw.gpu.enabled.*/hw.gpu.enabled=yes/' "$AVD_INI" || true; \
        sed -i 's/hw.gpu.mode.*/hw.gpu.mode=swiftshader_indirect/' "$AVD_INI" || true; \
        grep -q "^hw.gpu.enabled=" "$AVD_INI" || echo "hw.gpu.enabled=yes" >> "$AVD_INI"; \
        grep -q "^hw.gpu.mode=" "$AVD_INI" || echo "hw.gpu.mode=swiftshader_indirect" >> "$AVD_INI"; \
        grep -q "^hw.keyboard=" "$AVD_INI" || echo "hw.keyboard=yes" >> "$AVD_INI"; \
        grep -q "^disk.dataPartition.size=" "$AVD_INI" || echo "disk.dataPartition.size=6G" >> "$AVD_INI"; \
    fi

# ── Gradle wrapper ───────────────────────────────────────────────────
COPY gradle gradle/
COPY gradlew gradlew
COPY gradle.properties gradle.properties
COPY build.gradle /workspace/build.gradle
COPY settings.gradle /workspace/settings.gradle
RUN chmod +x /workspace/gradlew && \
    mkdir -p /workspace/app/src/main/java && \
    ./gradlew dependencies --no-daemon || true && \
    rm -rf /workspace/app/src

# ── Project source ───────────────────────────────────────────────────
COPY app /workspace/app

# ── Entrypoint ───────────────────────────────────────────────────────
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

RUN mkdir -p /workspace/app/build/reports/tests

ENTRYPOINT ["entrypoint.sh"]
CMD ["--no-emulator"]

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD ./gradlew --version >/dev/null 2>&1 || exit 1
