FROM maven:3.9-amazoncorretto-17
# 添加jar到镜像并命名为user.jar
ADD SlimAgent-1.0-SNAPSHOT.jar slim-agent-backend.jar
# 镜像启动后暴露的端口
EXPOSE 9900
# jar运行命令，参数使用逗号隔开
ENTRYPOINT ["java","-jar","slim-agent-backend.jar","--spring.profiles.active=prod"]