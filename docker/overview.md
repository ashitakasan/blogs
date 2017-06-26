## Debian jessis  Installation
新的 docker 版本不在使用 `docker.io` 作为软件包名称，改用 `docker-engine`

### 更新apt库
uninstall 旧版本
```Shell
# apt-get purge "lxc-docker*"
# apt-get purge "docker.io*"
```

安装 apt 的 https 和 ca证书
```
# apt-get update
# apt-get install apt-transport-https ca-certificates gnupg2
```

添加 docker 的 GPG key
```
# apt-key adv  --keyserver hkp://ha.pool.sks-keyservers.net:80 \
       --recv-keys 58118E89F3A912897C070ADBF76221572C52609D
```

新建 /etc/apt/sources.list.d/docker.list，添加一行：
```
deb https://apt.dockerproject.org/repo debian-jessie main
```

更新 apt
```
# apt-get update
```

### 安装 docker
```
# apt-get install docker-engine
```

启动并测试
```
# service docker start
# docker run hello-world
```

输出如下：
```
Hello from Docker!
This message shows that your installation appears to be working correctly.

To generate this message, Docker took the following steps:
 1. The Docker client contacted the Docker daemon.
 2. The Docker daemon pulled the "hello-world" image from the Docker Hub.
 3. The Docker daemon created a new container from that image which runs the
    executable that produces the output you are currently reading.
 4. The Docker daemon streamed that output to the Docker client, which sent it
    to your terminal.

To try something more ambitious, you can run an Ubuntu container with:
 $ docker run -it ubuntu bash

Share images, automate workflows, and more with a free Docker Hub account:
 https://hub.docker.com

For more examples and ideas, visit:
 https://docs.docker.com/engine/userguide/
```

### 添加非 root 权限
```
$ sudo groupadd docker
$ sudo gpasswd -a ${USER} docker
$ sudo service docker restart
```

## 运行
测试镜像的运行
```
docker run docker/whalesay cowsay boo
```

## Build
新建一个 docker 描述文件 Dockerfile：
```Docker
FROM docker/whalesay:latest
RUN apt-get -y update && apt-get install -y fortunes
CMD /usr/games/fortune -a | cowsay
```
编译新的 docker 镜像：
```
docker build -t docker-whale .
```

### 编译过程
1. Docker 自身检查以确保它拥有它需要构建的一切
	```
	Sending build context to Docker daemon 2.048 kB
	```
2. Docker 检查它是否已经有本地的镜像，如果没有，从 Docker 中心获取，对应于 FROM 语句
	```
	Step 1 : FROM docker/whalesay:latest
	 ---> 6b362a9f73eb
	```
Dockerfile 文件中的每一行对应镜像中的一层；每运行一行命令，docker 都会新建一个临时容器，执行命令，执行完成后移除容器；每个步骤结束时，将打印一个 ID (Dockerfile 限制不能超过 127 条命令)
3. Docker 启动临时容器运行 whalesay 映像，在临时容器中，Docker 运行 Dockerfile 中下一个命令，docker 会输出该命令的输出
	```
	Step 2 : RUN apt-get -y update && apt-get install -y fortunes
	 ---> Running in 59dd7e59ea62
	Ign http://archive.ubuntu.com trusty InRelease
	......
	Processing triggers for libc-bin (2.19-0ubuntu6.6) ...
	 ---> 4413fd8f23c3
	Removing intermediate container 59dd7e59ea62
	```
当 RUN 命令完成时，将创建一个新图层，并删除中间容器
4. 创建一个新的中间容器，Docker 在 Dockerfile 中为 CMD 行添加一个层，并删除中间容器
	```
	Step 3 : CMD /usr/games/fortune -a | cowsay
	 ---> Running in 31d9970c8df8
	 ---> 07abb9b8e346
	Removing intermediate container 31d9970c8df8
	Successfully built 07abb9b8e346
	```

编译完成，运行结果：
```
$ docker run docker-whale
 _________________________________ 
/ And miles to go before I sleep. \
|                                 |
\ -- Robert Frost                 /
 --------------------------------- 
    \
     \
      \     
                    ##        .            
              ## ## ##       ==            
           ## ## ## ##      ===            
       /""""""""""""""""___/ ===        
  ~~~ {~~ ~~~~ ~~~ ~~~~ ~~ ~ /  ===- ~~~   
       \______ o          __/            
        \    \        __/             
          \____\______/   
```
