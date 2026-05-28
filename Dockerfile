# Droidvisor Android SDK Testing Environment
# 基于官方Android SDK镜像构建测试环境
FROM android-sdk:34

# 设置工作目录
WORKDIR /workspace

# 安装Gradle和必要的构建工具
# 使用国内镜像源加速下载（仅在构建时使用）
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && \
    apk add --no-cache bash curl unzip git gradle

# 配置Gradle Wrapper
# 复制项目的gradle wrapper文件
COPY gradle gradle/
COPY gradlew gradlew
COPY gradlew.bat gradlew.bat
COPY gradle.properties gradle.properties

# 设置Gradle权限
RUN chmod +x /workspace/gradlew

# 配置JUnit和Robolectric测试运行环境
# 安装OpenJDK 17（与项目构建要求一致）
RUN apt-get update && apt-get install -y openjdk-17-jdk && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 创建测试报告目录
RUN mkdir -p /workspace/app/build/reports/tests

# 设置环境变量
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=$PATH:$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 复制项目源码（测试时挂载）
COPY app /workspace/app
COPY build.gradle /workspace/build.gradle
COPY settings.gradle /workspace/settings.gradle

# 健康检查配置（激进轮询：间隔5秒）
HEALTHCHECK --interval=5s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:3000/health || exit 1

# 启动命令 - 运行单元测试
CMD ["/workspace/gradlew", "testDebugUnitTest"]
