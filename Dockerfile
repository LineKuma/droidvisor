# Droidvisor Android SDK Testing Environment
# 基于官方Android SDK镜像构建测试环境
FROM ubuntu:22.04

# 设置工作目录
WORKDIR /workspace

# 安装基础依赖
RUN apt-get update && apt-get install -y \
    wget \
    unzip \
    curl \
    git \
    openjdk-17-jdk \
    ca-certificates \
    ca-certificates-java \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# 下载并安装 Android SDK command line tools
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk

RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    cd ${ANDROID_HOME}/cmdline-tools && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip && \
    mv cmdline-tools latest && \
    rm cmdline-tools.zip

# 设置PATH
ENV PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 接受Android SDK许可并安装平台工具
RUN yes | sdkmanager --licenses || true && \
    sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 配置Gradle Wrapper
COPY gradle gradle/
COPY gradlew gradlew
COPY gradle.properties gradle.properties

RUN chmod +x /workspace/gradlew

# 设置环境变量
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

# 设置Gradle JVM参数允许TLS协议降级
ENV JAVA_TOOL_OPTIONS="-Djdk.tls.client.protocols=TLSv1,TLSv1.1,TLSv1.2"

# 清理可能的损坏缓存
RUN rm -rf /root/.gradle /workspace/.gradle 2>/dev/null || true

# 复制项目源码（测试时挂载）
COPY app /workspace/app
COPY build.gradle /workspace/build.gradle
COPY settings.gradle /workspace/settings.gradle

# 创建测试报告目录
RUN mkdir -p /workspace/app/build/reports/tests

# 启动命令 - 保持容器运行
CMD ["tail", "-f", "/dev/null"]