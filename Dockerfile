# 使用官方带 Java 21 的 Spark 镜像作为底座
FROM apache/spark:4.0.0-preview2-java21

USER root

# 设置工作目录为 Spark 默认的工作目录空间
WORKDIR /opt/spark/work-dir/workspace

# 1. 拷贝本地编译好的 JAR 包
COPY target/*.jar ./app.jar

# 2. 拷贝并配置启动脚本
COPY workspace/entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh

# 3. 准备日志存放目录
RUN mkdir -p ./eventlog

# 暴露端口：8081 (本项目), 18080 (Spark History Server)
EXPOSE 8081 18080

ENTRYPOINT ["/bin/bash", "./entrypoint.sh"]