1. 启动类
```
@SpringBootApplication
public class StartApplication {

    public static void main(String[] args) {
        SpringApplication.run(StartApplication.class, args);
    }

}
```
2. run方法中做了什么
```
    public ConfigurableApplicationContext run(String... args) {
        // 当前时间
		long startTime = System.nanoTime();
		// 创建启动上下文对象
		DefaultBootstrapContext bootstrapContext = createBootstrapContext();
		ConfigurableApplicationContext context = null;
		// 配置headless模式,是在缺少显示屏、键盘或鼠标时的系统配置
		configureHeadlessProperty();
		// 获取启动事件监听器,注1
		SpringApplicationRunListeners listeners = getRunListeners(args);
		// 启动启动事件监听器,注2
		listeners.starting(bootstrapContext, this.mainApplicationClass);
		try {
			ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
			// 准备环境,注3
			ConfigurableEnvironment environment = prepareEnvironment(listeners, bootstrapContext, applicationArguments);
			// 忽略配置的bean
			configureIgnoreBeanInfo(environment);
			// 打印banner
			Banner printedBanner = printBanner(environment);
			// 创建容器
			context = createApplicationContext();
			context.setApplicationStartup(this.applicationStartup);
			// 准备应用上下文（spring容器前置处理）,注4
			prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
			// 刷新容器,注5
			refreshContext(context);
			// 刷新容器后的扩展接口（spring容器后置处理）
			// 这是一个空实现,留着后期扩展
			afterRefresh(context, applicationArguments);
			// 打印开始到现在时间
			Duration timeTakenToStartup = Duration.ofNanos(System.nanoTime() - startTime);
			if (this.logStartupInfo) {
				new StartupInfoLogger(this.mainApplicationClass).logStarted(getApplicationLog(), timeTakenToStartup);
			}
			// 发布监听应用上下文启动完成（发出启动结束事件）,注6
			listeners.started(context, timeTakenToStartup);
			// 执行runner,注7
			callRunners(context, applicationArguments);
		}
		catch (Throwable ex) {
		
		    // 异常处理
		    handleRunFailure(context, ex, listeners);
		    throw new IllegalStateException(ex);
		}
		try {
			Duration timeTakenToReady = Duration.ofNanos(System.nanoTime() - startTime);
			// 监听应用上下文运行中,注8
			listeners.ready(context, timeTakenToReady);
		}
		catch (Throwable ex) {
			handleRunFailure(context, ex, null);
			throw new IllegalStateException(ex);
		}
		// 返回最终构建的容器对象
		return context;
	}
```
3. 注1(获取启动事件监听器):getRunListeners()
> 读取spring-boot-*.jar/META-INFO/spring.factories文件中的 org.springframework.boot.SpringApplicationRunListener
> 
> 值为org.springframework.boot.context.event.EventPublishingRunListener
> 
> 返回一个EventPublishingRunListener实例启动事件发布监听器

```
	private SpringApplicationRunListeners getRunListeners(String[] args) {
		Class<?>[] types = new Class<?>[] { SpringApplication.class, String[].class };
		return new SpringApplicationRunListeners(logger,
				getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args),
				this.applicationStartup);
	}

	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
		return getSpringFactoriesInstances(type, new Class<?>[] {});
	}

	private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
		ClassLoader classLoader = getClassLoader();
		// Use names and ensure unique to protect against duplicates
		Set<String> names = new LinkedHashSet<>(SpringFactoriesLoader.loadFactoryNames(type, classLoader));
		List<T> instances = createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);
		AnnotationAwareOrderComparator.sort(instances);
		return instances;
	}
```
4. 注2(启动启动事件监听器):listeners.starting()
> EventPublishingRunListener的starting()方法
> 
> 创建了一个ApplicationStartingEvent对象,其实就是监听应用启动的事件
> 
> this.initialMulticaster是一个SimpleApplicationEventMulticaster
```
	@Override
	public void starting(ConfigurableBootstrapContext bootstrapContext) {
		this.initialMulticaster
				.multicastEvent(new ApplicationStartingEvent(bootstrapContext, this.application, this.args));
	}
```
> SimpleApplicationEventMulticaster的multicastEvent()方法
```
	public void multicastEvent(final ApplicationEvent event, @Nullable ResolvableType eventType) {
        ResolvableType type = eventType != null ? eventType : this.resolveDefaultEventType(event);
        // 获取线程池,为每个监听事件创建一个线程
        Executor executor = this.getTaskExecutor();
        // 根据 ApplicationStartingEvent事件类型找到对应的监听器并迭代
        Iterator var5 = this.getApplicationListeners(event, type).iterator();

        while(var5.hasNext()) {
            ApplicationListener<?> listener = (ApplicationListener)var5.next();
            if (executor != null) {
                executor.execute(() -> {
                    this.invokeListener(listener, event);
                });
            } else {
                this.invokeListener(listener, event);
            }
        }

    }
```
5. 注3(准备环境):prepareEnvironment()
```
	private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners,
			DefaultBootstrapContext bootstrapContext, ApplicationArguments applicationArguments) {
		// Create and configure the environment
		// 因为我们pom中引入了web依赖,所以这里是一个servlet环境,ApplicationServletEnvironment
		ConfigurableEnvironment environment = getOrCreateEnvironment();
		// 配置环境
		configureEnvironment(environment, applicationArguments.getSourceArgs());
		ConfigurationPropertySources.attach(environment);
		// 环境准备完成
		listeners.environmentPrepared(bootstrapContext, environment);
		DefaultPropertiesPropertySource.moveToEnd(environment);
		Assert.state(!environment.containsProperty("spring.main.environment-prefix"),
				"Environment prefix cannot be set via properties.");
		bindToSpringApplication(environment);
		if (!this.isCustomEnvironment) {
			environment = convertEnvironment(environment);
		}
		ConfigurationPropertySources.attach(environment);
		return environment;
	}
```
> 配置环境
```
	protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
		if (this.addConversionService) {
		    //转换器
		    environment.setConversionService(new ApplicationConversionService());
		}
		// 配置属性资源文件
		configurePropertySources(environment, args);
		// 配置文件
		configureProfiles(environment, args);
	}
```
> ApplicationConversionService转换器
```
	public static void configure(FormatterRegistry registry) {
		DefaultConversionService.addDefaultConverters(registry);
		DefaultFormattingConversionService.addDefaultFormatters(registry);
		// 格式转换
		addApplicationFormatters(registry);
		// 类型转换
		addApplicationConverters(registry);
	}
	// 格式转换
	public static void addApplicationFormatters(FormatterRegistry registry) {
		registry.addFormatter(new CharArrayFormatter());
		registry.addFormatter(new InetAddressFormatter());
		registry.addFormatter(new IsoOffsetFormatter());
	}
	// 类型转换
	public static void addApplicationConverters(ConverterRegistry registry) {
		addDelimitedStringConverters(registry);
		registry.addConverter(new StringToDurationConverter());
		registry.addConverter(new DurationToStringConverter());
		registry.addConverter(new NumberToDurationConverter());
		registry.addConverter(new DurationToNumberConverter());
		registry.addConverter(new StringToPeriodConverter());
		registry.addConverter(new PeriodToStringConverter());
		registry.addConverter(new NumberToPeriodConverter());
		registry.addConverter(new StringToDataSizeConverter());
		registry.addConverter(new NumberToDataSizeConverter());
		registry.addConverter(new StringToFileConverter());
		registry.addConverter(new InputStreamSourceToByteArrayConverter());
		registry.addConverterFactory(new LenientStringToEnumConverterFactory());
		registry.addConverterFactory(new LenientBooleanToEnumConverterFactory());
		if (registry instanceof ConversionService) {
			addApplicationConverters(registry, (ConversionService) registry);
		}
	}
```
> 环境准备完成
>
> 与之前启动启动事件监听器类似,由应用启动的事件变成了应用环境准备事件
```
	@Override
	public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext,
			ConfigurableEnvironment environment) {
		this.initialMulticaster.multicastEvent(
		        // 创建应用环境准备事件对象
				new ApplicationEnvironmentPreparedEvent(bootstrapContext, this.application, this.args, environment));
	}
```
6. 注4(准备应用上下文):prepareContext()
```
	private void prepareContext(DefaultBootstrapContext bootstrapContext, ConfigurableApplicationContext context,
			ConfigurableEnvironment environment, SpringApplicationRunListeners listeners,
			ApplicationArguments applicationArguments, Banner printedBanner) {
		// 设置环境参数
		context.setEnvironment(environment);
		// 设置后处理应用上下文
		postProcessApplicationContext(context);
		// 从spring-boot-*.jar/META-INFO/spring.factories中加载org.springframework.context.ApplicationContextInitializer
		applyInitializers(context);
		// EventPublishingRunListener发布应用上下文事件
		listeners.contextPrepared(context);
		// 打印启动日志
		bootstrapContext.close(context);
		if (this.logStartupInfo) {
			logStartupInfo(context.getParent() == null);
			logStartupProfileInfo(context);
		}
		// Add boot specific singleton beans
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
		if (printedBanner != null) {
			beanFactory.registerSingleton("springBootBanner", printedBanner);
		}
		if (beanFactory instanceof AbstractAutowireCapableBeanFactory) {
			((AbstractAutowireCapableBeanFactory) beanFactory).setAllowCircularReferences(this.allowCircularReferences);
			if (beanFactory instanceof DefaultListableBeanFactory) {
				((DefaultListableBeanFactory) beanFactory)
						.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
			}
		}
		if (this.lazyInitialization) {
			context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
		}
		// Load the sources
		// 获取所有资源
		Set<Object> sources = getAllSources();
		Assert.notEmpty(sources, "Sources must not be empty");
		// 创建BeanDefinitionLoader加载器,加载注册所有资源
		load(context, sources.toArray(new Object[0]));
		// EventPublishingRunListener发布应用上下文,加载事件
		listeners.contextLoaded(context);
	}
```
7. 注5(刷新容器):refreshContext()

```
    public void refresh() throws BeansException, IllegalStateException {
        synchronized(this.startupShutdownMonitor) {
            StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");
            // 准备刷新上下文
            this.prepareRefresh();
            // 通知子类刷新内部工厂
            ConfigurableListableBeanFactory beanFactory = this.obtainFreshBeanFactory();
            // 准备bean工厂
            this.prepareBeanFactory(beanFactory);

            try {
                // 允许在上下文子类中对bean工厂进行后处理 
                this.postProcessBeanFactory(beanFactory);
                StartupStep beanPostProcess = this.applicationStartup.start("spring.context.beans.post-process");
                this.invokeBeanFactoryPostProcessors(beanFactory);
                // 注册后置处理器
                this.registerBeanPostProcessors(beanFactory);
                beanPostProcess.end();
                // 初始化信息资源
                this.initMessageSource();
                // 初始化上下文事件发布器
                this.initApplicationEventMulticaster();
                // 初始化其他自定义bean
                this.onRefresh();
                // 注册监听器
                this.registerListeners();
                this.finishBeanFactoryInitialization(beanFactory);
                // 完成刷新,清缓存,初始化生命周期,事件发布等
                this.finishRefresh();
            } catch (BeansException var10) {
                if (this.logger.isWarnEnabled()) {
                    this.logger.warn("Exception encountered during context initialization - cancelling refresh attempt: " + var10);
                }
                // 销毁bean
                this.destroyBeans();
                this.cancelRefresh(var10);
                throw var10;
            } finally {
                this.resetCommonCaches();
                contextRefresh.end();
            }

        }
    }
```
> 初始化其他自定义bean,this.onRefresh();
> 在这里创建了tomcat对象,springboot一键启动的关键
```
	@Override
	protected void onRefresh() {
		super.onRefresh();
		try {
			// 创建web服务
			createWebServer();
		}
		catch (Throwable ex) {
			throw new ApplicationContextException("Unable to start web server", ex);
		}
	}
	
	private void createWebServer() {
		WebServer webServer = this.webServer;
		ServletContext servletContext = getServletContext();
		if (webServer == null && servletContext == null) {
			StartupStep createWebServer = this.getApplicationStartup().start("spring.boot.webserver.create");
			ServletWebServerFactory factory = getWebServerFactory();
			createWebServer.tag("factory", factory.getClass().toString());
			// 获取到tomcat
			this.webServer = factory.getWebServer(getSelfInitializer());
			createWebServer.end();
			getBeanFactory().registerSingleton("webServerGracefulShutdown",
					new WebServerGracefulShutdownLifecycle(this.webServer));
			getBeanFactory().registerSingleton("webServerStartStop",
					new WebServerStartStopLifecycle(this, this.webServer));
		}
		else if (servletContext != null) {
			try {
				getSelfInitializer().onStartup(servletContext);
			}
			catch (ServletException ex) {
				throw new ApplicationContextException("Cannot initialize servlet context", ex);
			}
		}
		initPropertySources();
	}
```
> 创建tomcat对象并设置参数
```
	@Override
	public WebServer getWebServer(ServletContextInitializer... initializers) {
		if (this.disableMBeanRegistry) {
			Registry.disableRegistry();
		}
		Tomcat tomcat = new Tomcat();
		File baseDir = (this.baseDirectory != null) ? this.baseDirectory : createTempDir("tomcat");
		tomcat.setBaseDir(baseDir.getAbsolutePath());
		for (LifecycleListener listener : this.serverLifecycleListeners) {
			tomcat.getServer().addLifecycleListener(listener);
		}
		Connector connector = new Connector(this.protocol);
		connector.setThrowOnFailure(true);
		tomcat.getService().addConnector(connector);
		customizeConnector(connector);
		tomcat.setConnector(connector);
		tomcat.getHost().setAutoDeploy(false);
		configureEngine(tomcat.getEngine());
		for (Connector additionalConnector : this.additionalTomcatConnectors) {
			tomcat.getService().addConnector(additionalConnector);
		}
		prepareContext(tomcat.getHost(), initializers);
		// 在这里启动tomcat
		return getTomcatWebServer(tomcat);
	}
```
8. 注6(发布监听应用上下文启动完成):listeners.started()
> 这里调用context.publishEvent()方法,发布应用启动事件ApplicationStartedEvent
```
	@Override
	public void started(ConfigurableApplicationContext context, Duration timeTaken) {
		context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context, timeTaken));
		AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
	}
```
9. 注7(执行runner):callRunners()
> 获取所有ApplicationRunner、CommandLineRunner来初始化一些参数
```
	private void callRunners(ApplicationContext context, ApplicationArguments args) {
		List<Object> runners = new ArrayList<>();
		runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
		runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());
		AnnotationAwareOrderComparator.sort(runners);
		for (Object runner : new LinkedHashSet<>(runners)) {
			if (runner instanceof ApplicationRunner) {
				callRunner((ApplicationRunner) runner, args);
			}
			if (runner instanceof CommandLineRunner) {
				callRunner((CommandLineRunner) runner, args);
			}
		}
	}
```
10. 注8(监听应用上下文运行中):listeners.ready()
> 发布一个ApplicationReadyEvent事件
```
	@Override
	public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
		context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context, timeTaken));
		AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
	}
```