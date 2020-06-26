# 简介

spring-bean 组件是 Spring IoC 的核心，我们可以使用它的 beanFactory 来获取所需的对象，对象的实例化、属性装配和初始化等都可以交给 spring 来管理。 本文将从`DefaultListableBeanFactory.getBean(Class)`方法开始分析获取 bean 的过程，主要内容如下，由于篇幅较长，可以根据需要选择阅读：

1. beanFactory 的设计
2. 多个 beanName 的处理
3. 获取单例 bean
4. 创建单例 bean
5. bean 的实例化
6. bean 的属性装配
7. bean 的初始化（省略）

spring-bean 的源码比较多，有些不影响整体分析思路的代码会被省略掉（代码中会注明），另外，想要分析所有的代码可能不大现实，所以，针对部分内容，我会点到为止，例如，本文只分析单例 bean 而不分析多例 bean。

# 前篇回顾

上篇博客[Spring源码系列(一)--详细介绍bean组件](https://www.cnblogs.com/ZhangZiSheng001/p/13126053.html)介绍了 bean 组件的一些重要理论概念，并通过例子演示如何使用 bean 组件。这里回顾下，这几个概念非常重要，是 bean 组件的理论基础：

1. **实例化、属性装配和初始化的概念**。 实例化指创建出一个新的对象；属性装配指给对象的成员属性赋值；  初始化指调用对象的初始化方法。 
2. **什么是 bean**：某个类的实例或描述对象，被注册到了 Spring IoC 容器，这时通过 Spring IoC 容器获取的这个类的对象就是 bean。
3. **什么是 beanFactory**：一个工厂，用于注册 bean 和获取 bean。
5. **什么是 beanDefinition**：一个描述对象，用来描述 bean 的实例化、属性装配、初始化等信息。

# beanFactory的设计

从客户端来看，一个完整的 beanFactory 工厂包含以下基本功能:

1. 注册别名。对应下图的`AliasRegistry`接口。
2. 注册单例对象。对应下图的`SingletonBeanRegistry`接口。
3. 注册`BeanDefinition`对象。对应下图的`BeanDefinitionRegistry`接口。
4. 获取 bean。对应下图的`BeanFactory`接口。

在 spring-bean 组件中，`DefaultListableBeanFactory`就是一个完整的 beanFactory 工厂，也可以说是一个 IoC 容器。

![BeanFactoryUML_01](https://img2020.cnblogs.com/blog/1731892/202006/1731892-20200614181541484-2054058872.png)

 `BeanFactory`还有几个扩展接口，用的比较多的可能是`ConfigurableBeanFactory`和`AutowireCapableBeanFactory`：

1. `HierarchicalBeanFactory`用于提供父子工厂的支持。例如，当前 beanFactory 找不到 bean 时，会尝试从 parent beanFactory 中获取。
2. `ConfigurableBeanFactory`用于提供配置 beanFactory 的支持。例如，注册`BeanPostProcessor`、注册 `TypeConverter`、注册`OrderComparator`等。
3. `ListableBeanFactory`用于提供批量获取 bean 的支持（不包含父工厂的 bean）。例如，我们可以根据类型获取 beanName-bean 的 map。
4. `AutowireCapableBeanFactory`用于提供实例化、属性装配、初始化等一系列管理 bean 生命周期的支持。 例如，该接口包含了 createBean、autowireBean、initializeBean、destroyBean 等方法。

当我们注册 bean 时，根据注册方式的不同，bean 的注册信息会被放入两个不同的地方。

```java
class DefaultSingletonBeanRegistry {
	// beanName=singletonObject键值对
    // 除了registerSingleton的会放在这里，registerBeanDefinition生成的单例bean实例也会放在这里
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
}
class DefaultListableBeanFactory {
	// beanName=beanDefination键值对
	private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<>(256);
}
```

接下来开始分析源码，注册 bean 比较简单，这里就不看了，我们直接看 getBean(Class) 的代码。

# 从getBean(requiredType)方法开始

进入到 `DefaultListableBeanFactory.getBean(Class)`方法，并逐渐展开。在`DefaultListableBeanFactory.resolveBean(ResolvableType, Object[], boolean)`方法中，如果当前 beanFactory 中获取不到这个 bean，将尝试从 parent beanFactory 中获取，这也说明了一点：**父子 beanFactory 中允许存在相同 beanName 的 bean，只是获取时当前 beanFactory 的优先级更高一些**。

```java
	public <T> T getBean(Class<T> requiredType) throws BeansException {
        // 适配入参
        // 可以看到，我们获取bean时还可以指定构造参数
		return getBean(requiredType, (Object[]) null);
	}
	public <T> T getBean(Class<T> requiredType, @Nullable Object... args) throws BeansException {
		Assert.notNull(requiredType, "Required type must not be null");
        // 继续适配入参
        // 这里的第三个参数表示，如果指定类型对应的beanName不唯一时，true为返回null, false为抛出异常
		Object resolved = resolveBean(ResolvableType.forRawClass(requiredType), args, false);
        // 如果获取不到这个bean，抛出异常
		if (resolved == null) {
			throw new NoSuchBeanDefinitionException(requiredType);
		}
		return (T) resolved;
	}
	private <T> T resolveBean(ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) {
        // 这里的NamedBeanHolder就是简单的对bean实例封装了一层，不用太关注
		NamedBeanHolder<T> namedBean = resolveNamedBean(requiredType, args, nonUniqueAsNull);
        // 如果获取得到bean实例，则返回
		if (namedBean != null) {
			return namedBean.getBeanInstance();
		}
        // 如果没有，尝试从parent beanFactory中获取
        // 这部分代码省略······
		return null;
	}
```

# 存在多个beanName怎么办

通过 beanType 来获取 bean，可能会存在一个类型关联了多个 beanName 的情况，使用例子中我们说过，可以通过指定 beanDefination 的 isPrimary = true 或者注册比较器的方式来解决。接下来我们看下具体的处理过程。

进入到`DefaultListableBeanFactory.resolveNamedBean(ResolvableType, Object[], boolean)`方法。如果指定类型匹配到了多个 beanName，会进行以下处理：

1. 如果存在通过`registerSingleton`注册的 beanName，或者通过`registerBeanDefinition`注册且 `autowireCandidate = true` 的 beanName，则仅保留它们，并剔除其他的 beanName；
2. 如果还是存在多个 beanName，检查是否存在唯一一个通过`registerBeanDefinition`且`isPrimary = true`的（存在多个会报错），存在的话将它作为匹配到的唯一 beanName；
3. 如果还是存在多个 beanName，通过我们注册的`OrderComparator`来确定优先值最小的作为唯一  beanName，注意，通过`registerSingleton`注册的和通过`registerBeanDefinition`注册的，比较的对象是不一样的；
4. 如果还是存在多个 beanName，根据 nonUniqueAsNull，为 true 是返回 null，为 false 抛出`NoUniqueBeanDefinitionException`异常。

```java
	private <T> NamedBeanHolder<T> resolveNamedBean(
			ResolvableType requiredType, @Nullable Object[] args, boolean nonUniqueAsNull) throws BeansException {

		Assert.notNull(requiredType, "Required type must not be null");
        // 获取指定类型的所有beanName，可能匹配到多个
		String[] candidateNames = getBeanNamesForType(requiredType);
        
		// 如果指定类型匹配到了多个beanName，进行以下操作：
        // 如果存在通过registerSingleton注册的beanName，或者通过registerBeanDefinition注册且 autowireCandidate = true的beanName，则仅保留它们，并剔除其他的beanName；
		if (candidateNames.length > 1) {
			List<String> autowireCandidates = new ArrayList<>(candidateNames.length);
			for (String beanName : candidateNames) {
				if (!containsBeanDefinition(beanName) || getBeanDefinition(beanName).isAutowireCandidate()) {
					autowireCandidates.add(beanName);
				}
			}
			if (!autowireCandidates.isEmpty()) {
				candidateNames = StringUtils.toStringArray(autowireCandidates);
			}
		}
        
		// 如果只剩下一个beanName，那就根据beanName和beanType获取bean
		if (candidateNames.length == 1) {
			String beanName = candidateNames[0];
			return new NamedBeanHolder<>(beanName, (T) getBean(beanName, requiredType.toClass(), args));
		}
        
        // 如果存在多个，则还要进一步处理
		else if (candidateNames.length > 1) {
			Map<String, Object> candidates = new LinkedHashMap<>(candidateNames.length);
            // 遍历候选的beanName
			for (String beanName : candidateNames) {
                // 如果该beanName是通过registerSingleton注册的，且传入构造参数为空
                // 则获取该bean实例，并放入candidates
				if (containsSingleton(beanName) && args == null) {
					Object beanInstance = getBean(beanName);
					candidates.put(beanName, (beanInstance instanceof NullBean ? null : beanInstance));
				}
				else {
                    // 其他情况下，则获取该beanName对应的类型，并放入candidates
                    // 注意，这里的类型不一定是我们入参指定的类型，例如，如果我指定的是UserServiceFactoryBean.class，这里返回的却是UserService.class
					candidates.put(beanName, getType(beanName));
				}
			}
            // 如果里面存在唯一一个通过registerBeanDefinition注册的且isPrimary=true（存在多个会报错），则将它作为匹配到的唯一beanName
			String candidateName = determinePrimaryCandidate(candidates, requiredType.toClass());
            // 如果还是确定不了，则通过我们注册的OrderComparator来判断candidates中value的优先数，挑选优先数最小的value对应的key作为唯一的beanName
			if (candidateName == null) {
				candidateName = determineHighestPriorityCandidate(candidates, requiredType.toClass());
			}
			if (candidateName != null) {
				Object beanInstance = candidates.get(candidateName);
                // 如果candidates中的value本身就是一个bean实例，那么直接返回就好了
                // 如果不是，则根据beanName和beanType获取bean
				if (beanInstance == null || beanInstance instanceof Class) {
					beanInstance = getBean(candidateName, requiredType.toClass(), args);
				}
				return new NamedBeanHolder<>(candidateName, (T) beanInstance);
			}
            // 如果还是确定不了唯一beanName，且设置了nonUniqueAsNull=false（默认为false），则会抛错
			if (!nonUniqueAsNull) {
				throw new NoUniqueBeanDefinitionException(requiredType, candidates.keySet());
			}
		}
		
		return null;
	}
```

# 根据beanName和beanType获取bean

进入`AbstractBeanFactory.getBean(String, Class<T>, Object...)`。这个方法里包括四个步骤：

1. 转义name。主要指的是当 name 是别名或者是 “&” + factory beanName 形式时进行转义；
2. 如果是单例 bean 且构造参数为空，则会从 singletonObjects 中获取已生成的 bean，或者从 earlySingletonObjects/singletonFactories 中获取已经实例化但可能还没装配或初始化的 bean。如果获取到的不是 null，直接返回对应的 bean 实例；
3. 如果当前 beanFactory 没有指定的 beanName，则会去 parent beanFactory 中获取；
4. 如果当前 bean 需要依赖其他 bean，则会先获取依赖的 bean；
5. 根据 scope 选择生成单例 bean 还是多例 bean；
6. 进行类型检查，如果获取的 bean 不匹配，会先用我们注册的类型转换器转换，如果还是不匹配就抛出`BeanNotOfRequiredTypeException`。

```java
	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {
		// 适配入参
        // 这里最后一个参数指获取的bean是否纯粹用于类型检查，如果是的话，beanFactory不会标记这个bean正在生成中，仅对单例bean有用
		return doGetBean(name, requiredType, args, false);
	}
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(final String name, @Nullable final Class<T> requiredType,
			@Nullable final Object[] args, boolean typeCheckOnly) throws BeansException {
		// 转义我们传入的name，这里包括两个内容：
        // 1. 如果是别名，需要转换为别名对象的beanName;
        // 2. 如果是“&”+factoryBeanName,则需要去掉前面的“&”
		final String beanName = transformedBeanName(name);
		Object bean;

		// 获取单例
        // 注意，这里获取到的有可能是已经初始化，也有可能是还没初始化，甚至还没装配的bean
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			// 省略日志部分······
            
            // 获取bean，因为sharedInstance有可能是factoryBean，如果我们要的是factoryBean对应的bean，则还要getObject
			bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}

		else {
			// 如果当前线程已经在生成beanName对应的bean，就会抛错
			if (isPrototypeCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(beanName);
			}

			// 如果当前beanFactory没有指定的beanName，则会去parent beanFactory中获取
            // 这部分省略······
			
			// 这里标记指定bean正在创建中，一般对单例bean才有意义
			if (!typeCheckOnly) {
				markBeanAsCreated(beanName);
			}

			try {
                // 获取指定beanName对应的RootBeanDefinition对象
				final RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
                // 检查RootBeanDefinition，目前就是检查是否对应的类型为抽象类，是的话抛错
				checkMergedBeanDefinition(mbd, beanName, args);

				// 如果当前bean需要依赖其他bean，则会先获取依赖的bean
				// 这部分省略······

				// 创建单例bean
				if (mbd.isSingleton()) {
					sharedInstance = getSingleton(beanName, () -> {
						try {
                            // 进入创建bean或factoryBean
							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							destroySingleton(beanName);
							throw ex;
						}
					});
                    // 获取bean实例
					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}
				// 创建多例bean
				else if (mbd.isPrototype()) {
					Object prototypeInstance = null;
					try {
                        // 标记当前线程正在创建这个bean
						beforePrototypeCreation(beanName);
                        // 进入创建bean或factoryBean
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
                        // 去掉当前线程中这个bean正在创建的标记
						afterPrototypeCreation(beanName);
					}
					bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}
				// 接下来这种一般是自定义Scope的情况，这里省略不讨论
				else {
					// ·······
				}
			}
			catch (BeansException ex) {
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
		}

		// 如果获取到的bean实例不是我们指定的类型
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
                // 使用我们注册的类型转换器进行转换
				T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
                // 如果转换不了，则会抛错
				if (convertedBean == null) {
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				return convertedBean;
			}
			catch (TypeMismatchException ex) {
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		return (T) bean;
	}
```

由于单例 bean 和多例 bean 的创建差不多，本文只选择单例的来分析。

# 获取单例bean

进入`DefaultSingletonBeanRegistry.getSingleton(String, ObjectFactory)`。这个方法包括几个过程，主要就是处理一些多线程问题：

2. 获取指定 beanName 的 bean，如果已经存在，就不去创建，这时为了处理多线程同时创建 bean 的问题；
3. 如果当前 bean 已经在创建中，会抛出 BeanCurrentlyInCreationException，创建单例 bean 之前是有加锁的，按理不会出现这种情况；
4. 创建单例 bean；
6. 如果创建成功，将 bean 实例加入 singletonObjects，并且删除掉 singletonFactories 和 earlySingletonObjects 中对应的键值对。

```java
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
        // 这里我不是很理解，为什么使用singletonObjects作为锁
        // 因为从earlySingletonObjects/singletonFactories中获取已经实例化但可能还没装配或初始化的 bean时，用的锁也是singletonObjects，这样的话，提前暴露的机制好像就废掉了啊？？？TODO
		synchronized (this.singletonObjects) {
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
                // 如果当前beanFactory的单例正在销毁，则不允许创建单例
				if (this.singletonsCurrentlyInDestruction) {
					// 省略抛错······
				}
				
				// 判断当前bean是不是已经在创建中，是的话抛出BeanCurrentlyInCreationException
                // 由于加了锁，这种情况应该是不会发生的
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
                
                // 省略部分代码······
                
				try {
                    // 这里的执行的是createBean方法
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				}
                // 这种情况我不是很理解，singletonObjects的操作不应该被锁住了吗？TODO
				catch (IllegalStateException ex) {
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				}
                // 如果抛出的是BeanCreationException，
				catch (BeanCreationException ex) {
                    // 省略部分代码······
                        
					throw ex;
				}
				finally {
                    // 省略部分代码······
                    
                    // 如果当前bean不处于创建状态中，会抛出IllegalStateException
					afterSingletonCreation(beanName);
				}
            	// 如果创建成功，将bean实例加入singletonObjects，并且删除掉singletonFactories和earlySingletonObjects中对应的键值对
				if (newSingleton) {
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}
```

以上方法中，如果获取不到已生成的单例 bean，则会开始创建 bean。

# 创建单例bean

进入`AbstractAutowireCapableBeanFactory.createBean(String, RootBeanDefinition, Object[])`。这个方法包括以下过程：

1. 解析 beanType，并且再次包装`RootBeanDefinition`；
3. 执行我们注册的`InstantiationAwareBeanPostProcessor`的`postProcessBeforeInstantiation`方法，如果返回了非空对象，则将其返回。也就是说我们可以在该方法中自定义完成 bean 的实例化、装配和初始化。
4. 创建 bean。

```java
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		RootBeanDefinition mbdToUse = mbd;

		// 解析当前RootBeanDefinition对应生成的bean类型，并进行再次包装
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

		// 省略部分代码······

		try {
			// 执行我们注册的InstantiationAwareBeanPostProcessor的postProcessBeforeInstantiation方法。也就是说我们可以在该方法中自定义完成 bean 的实例化、装配和初始化。
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
            // 如果该方法返回bean，那就直接返回
			if (bean != null) {
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
            // 创建bean
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}
```

进入`AbstractAutowireCapableBeanFactory.doCreateBean(String, RootBeanDefinition, Object[])`。这个方法主要包含以下过程：

1. 实例化 bean；
2. 执行我们注册的`MergedBeanDefinitionPostProcessor`的`postProcessMergedBeanDefinition`方法；
3. 如果是单例，将还没装配和初始化的 bean 先暴露出去，即放在singletonFactories中，如果其他线程进来获取，可以将这个 bean 或 factoryBean 返回，而不需要等待；
4. 属性装配；
5. 初始化；
6. 将生成的 bean 放入 disposableBeans 中。

```java
	protected Object doCreateBean(final String beanName, final RootBeanDefinition mbd, final @Nullable Object[] args)
			throws BeanCreationException {

		BeanWrapper instanceWrapper = null;
        // 实例化
        // 如果是单例，尝试从factoryBeanInstanceCache中获取
		if (mbd.isSingleton()) {
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
        // 实例化bean
		if (instanceWrapper == null) {
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}
		final Object bean = instanceWrapper.getWrappedInstance();
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			mbd.resolvedTargetType = beanType;
		}

		// 执行我们注册的MergedBeanDefinitionPostProcessor的postProcessMergedBeanDefinition方法
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				try {
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				mbd.postProcessed = true;
			}
		}

		// 单例的可以将还没装配和初始化的bean先暴露出去，即放在singletonFactories中
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}
        
		Object exposedObject = bean;
		try {
            // 属性装配
			populateBean(beanName, mbd, instanceWrapper);
            // 初始化
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

		// 省略部分代码······

		// 将生成的bean或factoryBean放入disposableBeans中
		try {
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		return exposedObject;
	}
```

接下来将展开 bean 的实例化、属性装配和初始化。其中，实例化和属性装配的代码比较复杂，我们重点分析，至于初始化部分，则留给读者自行阅读。

# 实例化

进入`AbstractAutowireCapableBeanFactory.createBeanInstance(String, RootBeanDefinition, Object[])`。这个方法主要过程如下：

1. 解析 beanType，并对 beanType 进行一些必要的检查；
2. 通过我们设置的 InstanceSupplier 或 FactoryMethod 来直接获取 bean，如果有的话，直接返回该对象；
3. 如果构造参数为空，则可以复用已经解析好的构造对象（如果有的话）；
4. 执行我们注册的`SmartInstantiationAwareBeanPostProcessor`的`determineCandidateConstructors`获取构造对象数组；
5. 如果得到的数组不是空，或者 beanDefination 的装配模式为构造注入，或者 beanDefination 包含构造参数，或者我们传入的构造参数非空，则进入实例化 bean
6. 其他情况，使用无参构造来实例化。

```java
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// 解析bean类型
		Class<?> beanClass = resolveBeanClass(mbd, beanName);
		
        // 如果bean类型不是public的，则抛错
		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}
		
        // 通过RootBeanDefinition中定义的Supplier来获取实例化bean
		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			return obtainFromSupplier(instanceSupplier, beanName);
		}
		// 通过RootBeanDefinition中定义FactoryMethod来实例化bean
		if (mbd.getFactoryMethodName() != null) {
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// 如果构造参数为空，则可以复用已经解析好的构造对象（如果有的话）
		boolean resolved = false;
		boolean autowireNecessary = false;
		if (args == null) {
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					resolved = true;
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		if (resolved) {
			if (autowireNecessary) {
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				return instantiateBean(beanName, mbd);
			}
		}

		// 执行我们注册的SmartInstantiationAwareBeanPostProcessor的determineCandidateConstructors获取Constructor对象数组（如果有的话）
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
        // 如果得到的数组不是空，或者beanDefination的装配模式为构造注入，或者beanDefination包含构造参数，或者我们传入的构造参数非空，则进入实例化bean或factoryBean
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			return autowireConstructor(beanName, mbd, ctors, args);
		}
		
		// 省略部分代码······

		// 使用无参构造实例化bean或factoryBean
		return instantiateBean(beanName, mbd);
	}
```

实例化的方法包括有参构造实例化和无参构造实例化两种，本文只讨论有参构造实例化的情况。

## ConstructorArgumentValues和ArgumentsHolder

在继续分析之前，有必要了解下`ConstructorArgumentValues`和`ArgumentsHolder`这两个类。

首先，`ConstructorArgumentValues`用于定义构造方法的参数列表的值。spring 中，`ConstructorArgumentValues`一般被定义在 `BeanDefinition`对象中，它影响着 bean 的实例化，是 bean 实例化时选择构造对象的依据。

```java
public class ConstructorArgumentValues {
	// 索引+参数值
    // 例如，对应new User(int age, String name, String address)的构造方法，可以包含元素：0=new ValueHolder(18),2=new ValueHolder("北京")
	private final Map<Integer, ValueHolder> indexedArgumentValues = new LinkedHashMap<>();
	// 通用参数值
    // 例如，对应new User(int age, String name, String address)的构造方法，如果indexedArgumentValues中不包含name的值，则可以在genericArgumentValues中查找，我们只需要添加元素：new ValueHolder("zzs001", String.class)
	private final List<ValueHolder> genericArgumentValues = new ArrayList<>();
    
    // 内部类，代表一个参数的值
    public static class ValueHolder implements BeanMetadataElement {

		@Nullable
		private Object value;

		@Nullable
		private String type;

		@Nullable
		private String name;

		@Nullable
		private Object source;

		private boolean converted = false;

}
```

`ArgumentsHolder`是`ConstructorResolver`的内部类，和`ConstructorArgumentValues`一样，它也是用来定义构造方法的参数列表的值，区别在于，`ConstructorArgumentValues`的值是“未解析的”，而`ArgumentsHolder`包含了“未解析”（preparedArguments）、“解析未完成”（rawArguments）和"解析完成"（arguments）三种值。

为什么会这样呢？因为`ConstructorArgumentValues`中的参数值的类型不一定和构造方法中的匹配，包括两种情况：

1. 类型不同，但可以通过`TypeConverter`转换的类型。例如，在`new User(int age, String name, Address address)`的构造方法中，我可以在`ConstructorArgumentValues`添加`2=new AddressVO()`，这个时候只要 spring 能找到合适的转换器就能转换，这个转换过程为：**“解析未完成”（rawArguments） --》 "解析完成"（arguments）**。
2. 类型不同，参数的值指向其他 bean ，当然也可以是其他 spring 可识别的引用。例如，`new User(int age, String name, Address address)`的构造方法中，我可以在`ConstructorArgumentValues`添加`2=new RootBeanDefinition(Address.class)`，这个转换过程为：**“未解析”（preparedArguments） --》“解析未完成”（rawArguments）**。

```java
private static class ArgumentsHolder {

    public final Object[] rawArguments;

    public final Object[] arguments;

    public final Object[] preparedArguments;

    public boolean resolveNecessary = false;

}
```

理解完这两个类之后，我们继续分析实例化的源码。

## 有参构造实例化

进入到`AbstractAutowireCapableBeanFactory.autowireConstructor(String, RootBeanDefinition, Constructor<?>[], Object[])`方法。这里创建了一个`ConstructorResolver`对象并直接调用它的 autowireConstructor 方法。

```java
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}
```

进入`ConstructorResolver.autowireConstructor(String, RootBeanDefinition, Constructor<?>[], Object[])`。这个方法代码比较多，为了更好地理解，可以分成两种场景来看：

1. 入参里显式指定构造参数。这种场景的参数值默认都是解析过的，所以不需要解析，该场景要求对应的构造对象的参数数量必须和指定的一样。
2. `BeanDefinition`对象中指定`ConstructorArgumentValues`。这种场景的参数值需要经过两步转换，该场景要求对应的构造对象的参数数量不小于指定的数量。

```java
	public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd,
			@Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {

		BeanWrapperImpl bw = new BeanWrapperImpl();
		this.beanFactory.initBeanWrapper(bw);
		
        // 定义最终用于实例化对象的构造器
		Constructor<?> constructorToUse = null;
        // 定义存放（“未解析”、“解析未完成”、“解析完成”）构造参数的对象
		ArgumentsHolder argsHolderToUse = null;
        // 定义最终用于实例化对象的构造参数
		Object[] argsToUse = null;
		
        // 入参显式声明了构造参数（场景一），则不需要解析参数列表值，但需解析构造对象
		if (explicitArgs != null) {
			argsToUse = explicitArgs;
		}
		else {
			Object[] argsToResolve = null;
            // BeanDefinition对象中指定ConstructorArgumentValues（场景二），如果参数列表值或构造对象已经解析，则不需要再解析
			synchronized (mbd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached constructor...
					argsToUse = mbd.resolvedConstructorArguments;
					if (argsToUse == null) {
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			if (argsToResolve != null) {
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve, true);
			}
		}
		
        // 进入解析参数列表值和构造对象
		if (constructorToUse == null || argsToUse == null) {
			// 如果入参里没有显式指定构造对象的数组，使用反射方式获取
			Constructor<?>[] candidates = chosenCtors;
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
                    // BeanDefinition中可以定义是否包括非public的方法
					candidates = (mbd.isNonPublicAccessAllowed() ?
							beanClass.getDeclaredConstructors() : beanClass.getConstructors());
				}
				catch (Throwable ex) {
					// 省略代码······
				}
			}
			
            // 如果数组中只有一个无参构造，且入参和BeanDefinition中都未指定参数列表值，则标记该BeanDefinition对象的构造参数已解析，并实例化bean
			if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				// 省略代码······
			}

			// 判断是否需要解析构造
			boolean autowiring = (chosenCtors != null ||
					mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
            // 这里存放“解析未完成”的参数列表值
			ConstructorArgumentValues resolvedValues = null;
			
            // 获取要求构造参数的最小数量
			int minNrOfArgs;
            // 入参显式声明了构造参数（场景一），minNrOfArgs即为指定数组的长度
			if (explicitArgs != null) {
				minNrOfArgs = explicitArgs.length;
			}
			else {
                // BeanDefinition对象中指定ConstructorArgumentValues（场景二），则需要计算minNrOfArgs，并进行“未解析” --> “解析未完成”的转换
				ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
				resolvedValues = new ConstructorArgumentValues();
				minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
			}
			// 根据参数数量从小到大排列
			AutowireUtils.sortConstructors(candidates);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			Set<Constructor<?>> ambiguousConstructors = null;
			LinkedList<UnsatisfiedDependencyException> causes = null;
			
            // 遍历候选的构造对象
			for (Constructor<?> candidate : candidates) {
				// 获取当前构造对象的参数数量
				int parameterCount = candidate.getParameterCount();
				//  如果上一个循环已经找到匹配的构造对象，则跳出循环1
				if (constructorToUse != null && argsToUse != null && argsToUse.length > parameterCount) {
					break;
				}
                
                // 如果当前构造对象的参数数量小于minNrOfArgs，则遍历下一个
                // 注意，入参里显式指定构造参数（场景一）要求对应的构造对象的参数数量必须和指定的一样。BeanDefinition对象中指定ConstructorArgumentValues（场景二）要求对应的构造对象的参数数量不小于指定的数量
				if (parameterCount < minNrOfArgs) {
					continue;
				}

				ArgumentsHolder argsHolder;
                // 获取当前构造对象的参数类型数组
				Class<?>[] paramTypes = candidate.getParameterTypes();
                // BeanDefinition对象中指定ConstructorArgumentValues（场景二）的情况
				if (resolvedValues != null) {
                    // 进行“解析未完成”->“解析完成”的转换
					try {
                        // 这里是为了处理JDK6的ConstructorProperties注解，其他情况都会返回null。
						String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, parameterCount);
						if (paramNames == null) {
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
                                // 获取当前构造对象的参数名数组
								paramNames = pnd.getParameterNames(candidate);
							}
						}
                        // 创建ArgumentsHolder对象
						argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
								getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);
					}
					catch (UnsatisfiedDependencyException ex) {
						// 省略代码······
						continue;
					}
				}
                // 入参里显式指定构造参数（场景一）的情况
				else {
                    // 如果当前构造参数的数量小于指定参数的数量，则继续循环
					if (parameterCount != explicitArgs.length) {
						continue;
					}
                    // 创建ArgumentsHolder对象，因为不需要解析参数，所以，这种情况raw、prepared、resolved都是一样的
					argsHolder = new ArgumentsHolder(explicitArgs);
				}
				// 计算指定参数和当前构造的参数类型的差异值
				int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
						argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
				// 差异值小于阈值
				if (typeDiffWeight < minTypeDiffWeight) {
					// 得到匹配的构造对象和构造参数
					constructorToUse = candidate;
					argsHolderToUse = argsHolder;
					argsToUse = argsHolder.arguments;
					minTypeDiffWeight = typeDiffWeight;
					ambiguousConstructors = null;
				}
                // 差异值大于阈值，这种不考虑
				else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					// 省略代码······
				}
			}
			// 如果找不到合适的构造对象，则会抛错
			if (constructorToUse == null) {
				// 省略代码······
			}
			else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				// 省略代码······
			}
			// BeanDefinition对象中指定ConstructorArgumentValues（场景二），为了复用解析好的构造和参数列表，需要标记当前BeanDefinition的构造参数已解析
			if (explicitArgs == null && argsHolderToUse != null) {
				argsHolderToUse.storeCache(mbd, constructorToUse);
			}
		}

		Assert.state(argsToUse != null, "Unresolved constructor arguments");
        // 接下来就是使用构造对象和参数来实例化对象，就不往下看了。
		bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse));
		return bw;
	}
```

实例化部分比较难，主要还得先理解一些抽象概念，例如：两个场景、参数的转换等。

# 属性装配

进入`AbstractAutowireCapableBeanFactory.populateBean(String, RootBeanDefinition, BeanWrapper)`。这个方法包括以下过程：

1. 执行我们注册的`InstantiationAwareBeanPostProcessor`的`postProcessAfterInstantiation`方法，如果返回了 false，则不进行属性装配，直接返回；
2. 获取 beanDefinition 中的`PropertyValues`对象，根据 beanDefinition 设置的注入类型，填充`PropertyValues`对象；
3. 执行我们注册的`InstantiationAwareBeanPostProcessor`的`postProcessProperties`方法，可以对`PropertyValues`对象进行修改;
4. 依赖检查（如果设置了）；
5. 进行属性装配。

```java
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
        // 如果实例对象为空，则抛出异常或直接返回
		if (bw == null) {
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				return;
			}
		}

		// 执行我们注册的InstantiationAwareBeanPostProcessor的postProcessAfterInstantiation方法，如果返回了false，则不进行属性装配，直接返回
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
					if (!ibp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
						return;
					}
				}
			}
		}
		
        // 获取BeanDefinition对象中的PropertyValues，包含了name=value的PropertyValue对象的列表
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);
		
        // 根据我们设置的注入方式，填充
		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			// 按名字装配
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// 按类型装配
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}
		// beanFactory中是否注册了InstantiationAwareBeanPostProcessors
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
        // BeanDefinition对象中是否设置了依赖检查
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			if (pvs == null) {
                // 如果为空，再次从BeanDefinition对象中获取，TODO？
				pvs = mbd.getPropertyValues();
			}
			for (BeanPostProcessor bp : getBeanPostProcessors()) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					InstantiationAwareBeanPostProcessor ibp = (InstantiationAwareBeanPostProcessor) bp;
                    // 执行我们注册的InstantiationAwareBeanPostProcessor的postProcessProperties方法，可以对PropertyValues对象进行修改
					PropertyValues pvsToUse = ibp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
					// 省略部分代码······
					pvs = pvsToUse;
				}
			}
		}
        // 如果BeanDefinition对象中设置了依赖检查，则需要检查依赖设置
		if (needsDepCheck) {
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		if (pvs != null) {
            // 执行属性装配
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}
```

这个方法中主要涉及`autowireByName`、`autowireByType`和`applyPropertyValues`三个方法，前两个暂时不展开，只讲最后一个方法。

## 几个重要的知识点

在分析`applyPropertyValues`方法之前，我们需要知道一下几个知识点。这里以`User`这个类来展开例子。

```java
public class User {
    
    private String name;
    
    private int age;
    
    private Address address;
    
    private List<String> hobbies;
}
class Address {
    private String region;
    private String desc;
}
```

### propertyName的几种形式

当我们给 beanDefinition设置属性值时，一般都会这样采用这样的赋值，这里成为“普通形式”。

```java
rootBeanDefinition.getPropertyValues().add("name", "zzs001");
rootBeanDefinition.getPropertyValues().add("age", 18);
rootBeanDefinition.getPropertyValues().add("address", new Address("", ""));
rootBeanDefinition.getPropertyValues().add("hobbies", new ArrayList());
```

针对类型为 object、list、array、map 等成员属性，spring 还支持其他的赋值方式，如下，分别成为“嵌套对象形式”和“索引形式”：

```java
// 嵌套对象形式
rootBeanDefinition.getPropertyValues().add("address.region", "");
rootBeanDefinition.getPropertyValues().add("address.desc", "");
// 索引形式
rootBeanDefinition.getPropertyValues().add("hobbies[0]", "");
```

正是由于 propertyName 引入了多种的形式，所以，原本简单的赋值行为被搞得非常复杂。例如，嵌套对象形式还可以是这样：`foo.user.address.region`，几乎可以一直嵌套下去。

### PropertyAccessor

propertyAccessor 对象一般绑定了一个实例对象，通过`PropertyAccessor`接口的方法可以对对象的属性进行存取操作。属性装配中最终对成员属性赋值就是调用它的`setPropertyValue`方法。`AbstractNestablePropertyAccessor`中维护了一个 map，key 为当前绑定对象的属性名（不包含嵌套和索引），value 就是对于的`PropertyAccessor`对象。

```java
public abstract class AbstractNestablePropertyAccessor extends AbstractPropertyAccessor {
    private Map<String, AbstractNestablePropertyAccessor> nestedPropertyAccessors;
}
```

在上面的例子中，

```java
rootBeanDefinition.getPropertyValues().add("name", "zzs001");
rootBeanDefinition.getPropertyValues().add("age", 18);
```

这种形式共用一个绑定了`User`类型实例的`PropertyAccessor`对象。

```java
// 嵌套对象形式
rootBeanDefinition.getPropertyValues().add("address.region", "");
rootBeanDefinition.getPropertyValues().add("address.desc", "");
```

这种形式共用一个绑定了`Address`类型实例的`PropertyAccessor`对象，该对象和"address"这个名字关联起来维护在 nestedPropertyAccessors 中。

```java
// 索引形式
rootBeanDefinition.getPropertyValues().add("hobbies[0]", "");
```

这种形式也是一个绑定了`User`类型实例的`PropertyAccessor`对象，该对象和"hobbies"这个名字关联起来维护在 nestedPropertyAccessors 中。

### PropertyTokenHolder

`PropertyTokenHolder`是`AbstractNestablePropertyAccessor`的内部类，它更多的是针对“索引形式”的  propertyName。例如，"hobbies[0]"对于的`PropertyTokenHolder`中，actualName = hobbies，canonicalName = [0]，keys = {0}。

```java
	protected static class PropertyTokenHolder {

		public PropertyTokenHolder(String name) {
			this.actualName = name;
			this.canonicalName = name;
		}

		public String actualName;

		public String canonicalName;

		@Nullable
		public String[] keys;
	}
```

接下来继续分析属性装配的代码。

## applyPropertyValues

进入`AbstractAutowireCapableBeanFactory.applyPropertyValues(String, BeanDefinition, BeanWrapper, PropertyValues)`方法。和构造参数一样，设置成员属性的参数也需要经过“两次转换”，这里就不详细讲解。这个方法主要包括以下过程：

1. 获取属性对象列表，如果这个列表的属性对象都已经完成“两次转换”，则直接装配属性；
2. 遍历属性对象列表，分别进行两次转换，如果列表中没有类似`BeanDefinition`、`BeanDefinitionHolder`等的对象，则设置`PropertyValues`对象已经转换完成，下次调用这个方法不用再进行转换；
3. 属性装配。

```java
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
        // 如果没有需要注入的属性，直接返回
		if (pvs.isEmpty()) {
			return;
		}

		// 省略部分代码······

		MutablePropertyValues mpvs = null;
        
        // 获取属性对象列表
		List<PropertyValue> original;
		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
            // 如果所有属性对象已经完成“两次转换”，则直接装配属性
			if (mpvs.isConverted()) {
				try {
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			original = mpvs.getPropertyValueList();
		}
		else {
			original = Arrays.asList(pvs.getPropertyValues());
		}
		
        // 获取我们注册的类型转换器
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
        // 创建第一次转换所用的解析器
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);
		
        // 定义一个列表，用于存放完成“两次转换”的属性对象
		// 这注意，这里并没有进行所谓的复制，不要被命名迷惑了
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		boolean resolveNecessary = false;
        // 遍历属性对象
		for (PropertyValue pv : original) {
            // 当前属性对象已经完成“两次转换”，直接添加到列表
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}
			else {
				String propertyName = pv.getName();
				Object originalValue = pv.getValue();
				// 省略部分代码······
                // 第一次转换
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				Object convertedValue = resolvedValue;
                // 如果当前属性为可写属性，且属性名不是类似于foo.bar或addresses[0]的形式，则需要进行第二次转换
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) {
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				// 如果转换后的属性对象和初始对象一样，一般指的是普通对象，而不是BeanDefinition、BeanDefinitionHolder等
				if (resolvedValue == originalValue) {
                    // 如果需要第二次转换，则设置复用的目标对象
					if (convertible) {
						pv.setConvertedValue(convertedValue);
					}
                    // 将原属性对象添加到列表
					deepCopy.add(pv);
				}
                // 这种情况不考虑
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
                // 其他情况
				else {
                    // 标记每次都需要解析
					resolveNecessary = true;
                    // 将原属性对象添加到列表
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
        // 如果不包含BeanDefinition、BeanDefinitionHolder等对象，则设置PropertyValues为已转换，这样下次调用这个方法，就不需要进行任何的转换了
		if (mpvs != null && !resolveNecessary) {
			mpvs.setConverted();
		}

		// 属性装配
		try {
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}
```

进入`AbstractPropertyAccessor.setPropertyValues(PropertyValues)`方法。这里遍历属性对象列表，逐个进赋值操作。

```java
	public void setPropertyValues(PropertyValues pvs) throws BeansException {
        // 入参适配
        // 后面两个参数分别代表：是否忽略NotWritablePropertyException异常、是否忽略NullValueInNestedPathException异常
		setPropertyValues(pvs, false, false);
	}
	public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid)
			throws BeansException {

        // 获取属性对象列表
		List<PropertyValue> propertyValues = (pvs instanceof MutablePropertyValues ?
				((MutablePropertyValues) pvs).getPropertyValueList() : Arrays.asList(pvs.getPropertyValues()));
		for (PropertyValue pv : propertyValues) {
            	// 省略try-catch的代码和其他异常相关的代码······
				setPropertyValue(pv);
            }
	}
```

## setPropertyValue

进入`AbstractNestablePropertyAccessor.setPropertyValue(PropertyValue)`。这个方法包括以下过程：

1. 获取 propertyName 对应的`PropertyAccessor`对象，这里将解析“嵌套对象形式”的 propertyName；
2. 创建`PropertyTokenHolder`对象，这里将解析“索引形式”的 propertyName；
3. 使用`PropertyAccessor`对象进行赋值操作。

```java
	public void setPropertyValue(PropertyValue pv) throws BeansException {
        // 适配入参
		setPropertyValue(pv.getName(), pv.getValue());
	}	
	public void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException {
		AbstractNestablePropertyAccessor nestedPa;
		try {
            // 获取propertyName对应的PropertyAccessor对象，这里将解析“嵌套对象形式”的propertyName
            // 如果缓存里有的话，将复用
			nestedPa = getPropertyAccessorForPropertyPath(propertyName);
		}
		catch (NotReadablePropertyException ex) {
			throw new NotWritablePropertyException(getRootClass(), this.nestedPath + propertyName,
					"Nested property in path '" + propertyName + "' does not exist", ex);
		}
        // 创建PropertyTokenHolder对象，这里将解析“索引形式”的propertyName
		PropertyTokenHolder tokens = getPropertyNameTokens(getFinalPath(nestedPa, propertyName));
        // 使用PropertyAccessor对象进行赋值操作
		nestedPa.setPropertyValue(tokens, new PropertyValue(propertyName, value));
	}
```

进入`AbstractNestablePropertyAccessor.setPropertyValue(PropertyTokenHolder, PropertyValue)`方法。这里根据 propertyName 是否为“索引形式”调用不同的方法。

```java
	protected void setPropertyValue(PropertyTokenHolder tokens, PropertyValue pv) throws BeansException {
		if (tokens.keys != null) {
			processKeyedProperty(tokens, pv);
		}
		else {
			processLocalProperty(tokens, pv);
		}
	}
```

这里我们不看 propertyName 为“索引形式”的方法，只看`processLocalProperty`。

```java
	private void processLocalProperty(PropertyTokenHolder tokens, PropertyValue pv) {
        // 获取actualName对应的PropertyHandler对象，如果有缓存则复用
		PropertyHandler ph = getLocalPropertyHandler(tokens.actualName);
		if (ph == null || !ph.isWritable()) {
			// 省略部分代码······
		}

		Object oldValue = null;
		try {
			Object originalValue = pv.getValue();
			Object valueToApply = originalValue;
			if (!Boolean.FALSE.equals(pv.conversionNecessary)) {
                // 因为我们的属性参数都是转换过的，所以这里不再看转换的代码
				if (pv.isConverted()) {
					valueToApply = pv.getConvertedValue();
				}
				else {
					// 省略部分代码······
				}
				pv.getOriginalPropertyValue().conversionNecessary = (valueToApply != originalValue);
			}
            // 接下来就是通过反射方式给属性赋值，后续再展开
			ph.setValue(valueToApply);
		}
		catch (Exception ex) {
			// 省略部分代码······
        }
	}

```

属性装配的代码分析就点到为止吧。

# 最后补充

以上基本看完 spring-bean 的源码。针对 getBean 的过程，本文未展开的内容包括：

1. 获取和创建多例 bean；
2. 无参构造实例化；
3. 属性装配中，属性值列表的填充（autowireByName和autowireByType）、属性名为索引形式的属性装配
4. bean 的初始化。

感兴趣的读者可以自行分析。另外，以上内容如有错误，欢迎指正。

最后，感谢阅读。

> 相关源码请移步：[ spring-beans](https://github.com/ZhangZiSheng001/spring-projects/tree/master/spring-beans)

> 本文为原创文章，转载请附上原文出处链接：[https://www.cnblogs.com/ZhangZiSheng001/p/13196228.html](https://www.cnblogs.com/ZhangZiSheng001/p/13196228.html)







