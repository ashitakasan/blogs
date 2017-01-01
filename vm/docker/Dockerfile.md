## Dockerfile 最佳实践
Docker 可以通过从 Dockerfile 中读取指令来自动构建映像，Dockerfile 是一个包含所有命令的文本文件，用于构建给定映像  

### 编写 Dockerfile 时需要遵循的准则和建议

#### 容器应该是临时的
由 Dockerfile 定义的映像生成的容器应该尽可能短暂，绝对最小的设置和配置

#### 使用 .dockerignore 文件
在大多数情况下，最好将每个 Dockerfile 放在一个空目录中；然后，只向该目录添加构建 Dockerfile 所需的文件。要增加构建的性能，您可以通过将 .dockerignore 文件添加到该目录来排除文件和目录，类似与 .gitignore

#### 避免安装不必要的包
为了减少复杂性，依赖性，文件大小和构建时间，应该避免安装额外的或不必要的包

#### 每个容器只运行一个进程
在几乎所有情况下，只应在一个容器中运行单个进程。  
将应用程序解耦到多个容器中，更容易进行水平扩展和重用容器。  
如果该服务依赖于另一个服务，请使用容器链接。

#### 最小化层数
需要在 Dockerfile 的可读性（以及因此的长期可维护性）和最小化容器使用的层数之间找到一个平衡

#### 多行参数排序
尽可能通过以字母数字方式排序多行参数来缓解以后的更改。这将避免包的重复，并使列表更容易更新

#### 构建缓存
在构建映像的过程中，Docker 将按照指定的顺序执行每个 Dockerfile 中的指令；当每个指令被检查时，Docker 将在缓存中查找可以重用的镜像，而不是创建一个新的镜像

_Docker 构建缓存的基本规则_
- 从已经在缓存中的基本映像开始，将下一个指令与从该映像导出的所有子映像进行比较，以查看有没有使用完全相同的指令构建的镜像
- 在大多数情况下，只需将 Dockerfile 中的指令与子镜像之一进行比较就足够了
- 对于 ADD 和 COPY 指令，检查镜像中文件的内容，并为每个文件计算校验和；在缓存查找期间，将校验和与现有映像中的校验和进行比较。如果文件中有任何更改，如内容和元数据，则缓存将失效
- 除了 ADD 和 COPY 命令，缓存检查不会查看容器中的文件来确定缓存匹配

一旦缓存失效，后续所有的 Dockerfile 命令都会生成新的镜像，缓存不会被使用

### Dockerfile 指令

#### FROM
只要有可能，使用当前的官方资料库作为您的形象的基础；推荐使用 Debian

#### LABEL
向镜像添加标签，以帮助按项目组织镜像，记录许可信息，帮助自动化等；对于每个标签，添加一行以 LABEL 开头，并使用一个或多个键值对  
_注意：如果字符串包含空格，则必须使用引号或必须转义空格_

#### RUN
为了使 Dockerfile 更易读，可理解和可维护，在用反斜杠分隔的多行上拆分长或复杂的 RUN 语句  

___apt-get___
- 应该避免 `RUN apt-get upgrade` 或 `dist-upgrade`，因为映像许多基本的必需包不会在非特权容器中升级
- 始终将 `RUN apt-get update` 与 `apt-get install -y` 组合在同一RUN语句中
- 在 RUN 语句中单独使用 `apt-get update` 会导致缓存问题；随后的 `apt-get install` 将会失败。
	```
	RUN apt-get update
	RUN apt-get install -y curl
	```
	更新为：
	```
	RUN apt-get update
	RUN apt-get install -y curl nginx
	```
	Docker 将初始和当前的指令视为相同，并重复使用之前步骤的缓存。因此，`apt-get update` 不会执行，因为构建将使用之前的缓存版本。因为 `apt-get update` 没有运行，curl 不会更新  

一个 `RUN apt-get` 的例子：
```
RUN apt-get update && apt-get install -y \
    aufs-tools \
    automake \
    build-essential \
    curl \
    dpkg-sig \
    libcap-dev \
    libsqlite3-dev \
    mercurial \
    reprepro \
    ruby1.9.1 \
    ruby1.9.1-dev \
    s3cmd=1.1.* \
 && rm -rf /var/lib/apt/lists/*
```
清理 apt 缓存并删除 /var/lib/apt/lists 有助于降低映像大小

#### CMD
CMD 指令应该用于运行镜像包含的软件以及任何参数：`CMD ['executable', 'param1', 'param2', ...]`

#### EXPOSE
EXPOSE 指令指示容器将在其上侦听连接的端口

#### ENV
为了使新软件更易于运行，您可以使用 ENV 更新容器安装的软件的 PATH 环境变量

#### ADD 或 COPY
尽管 ADD 和 COPY 在功能上相似，但一般来说，COPY 是优选的，因为它比 ADD 更透明  
COPY 仅支持将本地文件复制到容器中，而 ADD 具有一些不是立即显而易见的功能  
如果　Dockerfile 步骤中需要复制多个不同文件 ，请单独复制它们，这将确保每个步骤的构建缓存只有在特定所需文件更改时才会失效

#### ENTRYPOINT
ENTRYPOINT 的最佳用法是设置镜像的主命令，允许该镜像像该命令一样运行（然后使用 CMD 作为默认标志）

#### VOLUME
VOLUME 指令应用于公开由 docker 容器创建的任何数据库存储区域，配置存储或文件/文件夹

#### USER
如果服务可以在没有权限的情况下运行，请使用 USER 更改为 非root 用户  
应该避免安装或使用 sudo，因为它具有不可预测的 TTY 和信号转发行为，可能导致更多的问题

#### WORDDIR
为了清晰和可靠，您应该始终为 WORKDIR 使用绝对路径

#### ONBUILD
ONBUILD 命令在当前 Dockerfile 构建完成后执行。ONBUILD 在从当前镜像派生的任何子镜像中执行。将 ONBUILD 命令视为父 Dockerfile 给子 Dockerfile 的指令  
Docker build 在子 Dockerfile 中的任何命令之前执行 ONBUILD 命令







