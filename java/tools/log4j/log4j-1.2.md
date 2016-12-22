# Apache Log4j 1.2

log4j 是记录 java 程序日志文件的有用么工具，是许多其他的 java 工具包的基础。

## 三个组件
- Loggers
- Appenders
- Layouts

### Loggers
Loggers 组件主要用于打印日志。
1. Loggers 日志级别	 
	log4j 默认有 6 种级别，从低到高依次是：  
	`TRACE, DEBUG, INFO, WARN, ERROR, FATAL`；  
	每个 Logger 类都有一个默认的日志级别，仅当 Logger 的调用级别 __不低于__ Logger 的默认级别时，Logger 才会输出日志。
2. Loggers 的继承机制
	Logger 的默认级别是可以指定的，如果没有指定，则从父 Logger 继承；默认的，每个 Logger 都有一个唯一的名字（如类的全限定名），如果一个 Logger A 的名字是 另一个 Logger B 的名字的字符串前缀，那么 A 就是 B 的祖先，子 Logger 会从其最近的父 Logger 继承默认日志级别，除非显示的指定了日志级别。
3. Loggers 实例
	具有相同名称的 Logger 类都是同一个实例，如果在同一个 jvm 中不同的两个类中通过 `Logger.getLogger("")` 方法获取了两个名称相同的 Logger 类，那么它们指向同一个引用。


### Appenders
Appenders 组件主要用于指定日志的输出地址，并支持异步输出。  
通过 `Logger.addAppender()` 方法将 Appenders 添加到 Logger 中。
1. Appenders 的继承机制
	子 Logger 会从父 Logger 中继承 Appenders，然后该 Logger 会将其日志输出到所有的 Appenders 中。但是如果其某个父 Logger 中 additivity 字段设置为 false，则不会将日志输出到 该父 Logger 以上的祖先 Logger 中的 Appenders 中。

### Layouts
Layouts 负责根据用户指定的格式格式化日志，而 Appenders 负责将格式化的输出发送到其目的地；每个 Appender 都有一个 Layout。

### Configuration
- BasicConfigurator
- PropertyConfigurator  
	```Java
	PropertyConfigurator.configure("configuration file path");
	```
	config file:
	```TXT
	# Set root logger level to DEBUG and its only appender to A1.
	log4j.rootLogger=DEBUG, A1
	
	# A1 is set to be a ConsoleAppender.
	log4j.appender.A1=org.apache.log4j.ConsoleAppender
	
	# A1 uses PatternLayout.
	log4j.appender.A1.layout=org.apache.log4j.PatternLayout
	log4j.appender.A1.layout.ConversionPattern=%-4r [%t] %-5p %c %x - %m%n
	```
	多个 Appenders 的配置文件：
	```TXT
	log4j.rootLogger=debug, stdout, R

	log4j.appender.stdout=org.apache.log4j.ConsoleAppender
	log4j.appender.stdout.layout=org.apache.log4j.PatternLayout
	
	# Pattern to output the caller's file name and line number.
	log4j.appender.stdout.layout.ConversionPattern=%5p [%t] (%F:%L) - %m%n
	
	log4j.appender.R=org.apache.log4j.RollingFileAppender
	log4j.appender.R.File=example.log
	
	log4j.appender.R.MaxFileSize=100KB
	# Keep one backup file
	log4j.appender.R.MaxBackupIndex=1
	
	log4j.appender.R.layout=org.apache.log4j.PatternLayout
	log4j.appender.R.layout.ConversionPattern=%p %t %c - %m%n
	```

### log4j 默认初始化过程
1. 设置 log4j.defaultInitOverride 为 false 会让 log4j 跳过初始化阶段
2. 查找 log4j.configuration 系统属性，以该属性指定的文件为 log4j 配置文件
3. 如果没有找到该系统属性，尝试加载 log4j.xml 及(如果没有) log4j.properties，使用该文件为 log4j 配置文件，分别用 DOMConfigurator 及 PropertyConfigurator 配置
4. 如果配置了 log4j.configuratorClass 系统属性，则用该属性指定的 Class 文件来配置log4j
