#!/bin/bash

# ==============================================================================
# Spark Performance Insight 启动脚本
# 负责环境配置、启动 Spark History Server 以及启动后端分析服务
# ==============================================================================

# --- 1. 安装必要的系统组件 ---
echo ">>> [1/5] 安装必要的系统组件 (vim, bash)..."
apt-get update > /dev/null 2>&1
apt-get install -y bash --no-install-recommends > /dev/null 2>&1
apt-get install -y vim --no-install-recommends > /dev/null 2>&1

# --- 2. 配置环境变量 ---
echo ">>> [2/5] 注入系统级与用户级环境变量..."

# 2.1 强制设置系统级环境变量 (确保所有 Shell 实例生效)
cat >> /etc/bash.bashrc <<EOF
export SPARK_HOME=/opt/spark
export HADOOP_CONF_DIR=/etc/hadoop
export PATH=\$PATH:\$SPARK_HOME/bin:\$SPARK_HOME/sbin
EOF

# 2.2 设置用户级 PATH (方便 docker exec 使用)
cat >> ~/.bashrc <<'EOF'
export SPARK_HOME=/opt/spark
export PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin
EOF

source ~/.bashrc

# --- 3. 配置 Spark History Server ---
echo ">>> [3/5] 配置 Spark History Server 默认参数..."
LOG_DIR=${EVENT_LOG_PATH:-/opt/spark/work-dir/workspace/eventlog}
mkdir -p $LOG_DIR
mkdir -p /opt/spark/conf
cat > /opt/spark/conf/spark-defaults.conf <<EOF
spark.eventLog.enabled             true
spark.eventLog.dir                 file://$LOG_DIR
spark.history.fs.logDirectory      file://$LOG_DIR
spark.yarn.historyServer.address   http://localhost:18080
spark.history.ui.port              18080
EOF

# --- 4. 启动 History Server ---
echo ">>> [4/5] 启动 Spark History Server..."
/opt/spark/sbin/start-history-server.sh

# --- 5. 启动 Spark Performance Insight 应用 ---
echo ">>> [5/5] 启动 Spark Performance Insight 后端服务..."
# 检查当前目录下是否有 app.jar (Dockerfile 拷贝进去的位置或挂载位置)
JAR_PATH="./app.jar"
if [ ! -f "$JAR_PATH" ]; then
    # 回退方案：如果不在当前目录，尝试在绝对路径找
    JAR_PATH="/opt/spark/work-dir/workspace/app.jar"
fi

java -Dinsight.event-log-path=$LOG_DIR \
     -jar $JAR_PATH \
     --spring.config.additional-location=optional:file:./workspace/config/ \
     > ./insight-app.log 2>&1 &

echo ">>> ✅ 启动完成，正在持续追踪日志输出..."
# 等待日志生成
tail -f /opt/spark/logs/*org.apache.spark.deploy.history.HistoryServer*.out
