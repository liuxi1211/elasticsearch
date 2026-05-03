# Google Guice 终极实战指南：从“新对象”的枷锁中解放代码

你是否厌倦了在代码中写满 `new UserServiceImpl()`？是否被 Spring 庞大的 XML 配置压得喘不过气？如果你追求极致的类型安全、近乎零配置的启动速度，以及像手术刀一样精准的依赖管理，那么 Google Guice 就是你的不二之选。

这不仅仅是一个框架，这是一场关于**控制反转（IoC）**的革命。

## 第一章：为什么是 Guice？—— 拒绝臃肿，拥抱类型安全

Guice（读作 "juice"）由 Google 的“疯狂Bob”李杰（Bob Lee）开发，初衷非常纯粹：**杀死 XML 配置，利用 Java 本身的类型系统和注解来完成依赖注入。**

与 Spring 相比，Guice 的核心优势在于：
1.  **极致性能**：Guice 在运行时动态解析依赖，启动速度极快。相比 Spring 在启动时解析海量 XML 和进行类路径扫描，Guice 像一把热刀切入黄油，据说在某些场景下性能比 Spring 快十倍甚至更多。
2.  **类型安全**：所有的绑定关系都在 Java 代码中通过泛型和注解定义。如果你在编译期写错了绑定，IDE 会立刻报错，而不是等到运行时才抛出 `BeanCreationException`。
3.  **无侵入性**：你的业务代码不需要继承任何 Guice 的类，仅仅需要一个 `@Inject` 注解。

**一句话总结：Spring 是全能的重型航母，而 Guice 是精准制导的特种兵。**

## 第二章：核心三驾马车 —— Injector、Module 与 @Inject

Guice 的世界由三个核心概念构建而成，理解了它们，你就掌握了 80% 的 Guice。

### 1. Injector（注入器）：智能的装配工
`Injector` 是 Guice 的大脑。它根据你的配置（Module），负责创建对象、装配依赖，并把完整的对象交给你。你永远不需要自己调用 `new`，只需要向 Injector “索要”对象。

### 2. Module（模块）：装配蓝图
`Module` 是 Guice 的灵魂。你需要继承 `AbstractModule`，并重写 `configure()` 方法。这里是你定义“谁依赖谁”的地方。

### 3. @Inject：请求信号
这是 Guice 的信号弹。打在构造函数、字段或方法上，告诉 Guice：“这里需要你帮我填入一个依赖！”

### 🚀 实战：5分钟 Hello World

**Step 1: 引入依赖 (Maven)**
确保使用 Java 11+，并引入最新的 Guice 7.0（支持 Jakarta 命名空间）或 6.0（支持 javax）。
```xml
<dependency>
    <groupId>com.google.inject</groupId>
    <artifactId>guice</artifactId>
    <version>7.0.0</version>
</dependency>
```

**Step 2: 定义接口与实现**
```java
public interface MessageService {
    String getMessage();
}

public class EmailService implements MessageService {
    @Override
    public String getMessage() { return "Hello from Email!"; }
}
```

**Step 3: 创建配置模块**
这是 Guice 的“配置文件”，只不过它是强类型的 Java 代码。
```java
public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        // 关键：将接口绑定到实现类
        bind(MessageService.class).to(EmailService.class);
    }
}
```

**Step 4: 注入并使用**
```java
public class MyApplication {
    private final MessageService messageService;

    // 构造函数注入：推荐！
    @Inject
    public MyApplication(MessageService messageService) {
        this.messageService = messageService;
    }

    public void start() {
        System.out.println(messageService.getMessage());
    }

    public static void main(String[] args) {
        // 1. 创建注入器
        Injector injector = Guice.createInjector(new AppModule());
        // 2. 获取实例（Guice 会自动注入 EmailService）
        MyApplication app = injector.getInstance(MyApplication.class);
        app.start();
    }
}
```
**输出**： `Hello from Email!`

看到了吗？没有一行 XML，没有一个 `new` 关键字，`MyApplication` 甚至不知道 `EmailService` 的存在。这就是解耦的力量！

## 第三章：高级绑定技巧 —— 应对复杂场景

现实世界的依赖关系往往比上面的例子复杂得多。同一个接口有多个实现？需要传入原始类型参数？需要延迟加载？Guice 提供了丰富的绑定策略。

### 1. 命名绑定（@Named）：区分同接口的不同实现
当你有多个 `MessageService` 实现（比如 Email 和 SMS），Guice 需要知道注入哪一个。
```java
// 模块配置
bind(MessageService.class).annotatedWith(Names.named("SMS")).to(SmsService.class);
bind(MessageService.class).annotatedWith(Names.named("Email")).to(EmailService.class);

// 注入点
@Inject
public MyApp(@Named("SMS") MessageService smsService) { ... }
```

### 2. 提供者绑定（@Provides & Provider）：处理复杂创建逻辑
如果对象的创建需要复杂的逻辑（如读取配置、建立连接），不能简单地 `new`，使用 `@Provides`。
```java
public class DatabaseModule extends AbstractModule {
    @Override
    protected void configure() {} // 空的configure

    @Provides
    @Singleton // 保证单例
    public DataSource provideDataSource() {
        // 复杂的初始化逻辑
        BasicDataSource ds = new BasicDataSource();
        ds.setUrl("jdbc:mysql://localhost/db");
        ds.setUsername("user");
        return ds;
    }
}
```
或者实现 `Provider<T>` 接口，用于延迟加载（Lazy Loading）。

### 3. 实例绑定（toInstance）：绑定常量或无状态对象
对于已经创建好的对象或常量，直接绑定实例。
```java
bind(String.class).annotatedWith(Names.named("API Key")).toInstance("abc-123-xyz");
```

### 4. 集合绑定（Multibinder）：注入多个实现
如果你需要注入所有 `Validator` 的实现（EmailValidator, PasswordValidator），使用 `Multibinder`。
```java
Multibinder<Validator> validatorBinder = Multibinder.newSetBinder(binder(), Validator.class);
validatorBinder.addBinding().to(EmailValidator.class);
validatorBinder.addBinding().to(PasswordValidator.class);
// 注入时使用 Set<Validator>
```

## 第四章：作用域与生命周期 —— 单例与请求作用域

Guice 默认每次注入都创建一个新实例。但在实际应用中，我们需要控制对象的生命周期。

*   **@Singleton**：全局单例，整个 Injector 中只有一个实例。这是最常用的作用域。
*   **@RequestScoped**（需扩展）：在 Web 请求范围内有效。
*   **自定义作用域**：你可以定义自己的作用域注解。

**注意**：单例对象的依赖也必须是单例的（或者是无状态的），否则会引发并发问题。

## 第五章：AOP 与方法拦截 —— 横切关注点

Guice 内置了轻量级的 AOP 支持，无需引入 AspectJ。通过 `MethodInterceptor` 可以实现日志、事务、权限检查等。

```java
public class LoggingInterceptor implements MethodInterceptor {
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        System.out.println("Method " + invocation.getMethod().getName() + " is called.");
        return invocation.proceed(); // 执行原方法
    }
}

// 在模块中绑定拦截器
bindInterceptor(Matchers.any(), Matchers.annotatedWith(Loggable.class), new LoggingInterceptor());
```
只需在方法上加 `@Loggable` 注解，即可自动织入日志逻辑。

## 第六章：Guice vs Spring —— 终极对决

这是一个绕不开的话题，直接上干货对比：

| 维度 | Google Guice | Spring Framework |
| :--- | :--- | :--- |
| **配置方式** | **Java 代码 + 注解** (类型安全，重构友好) | XML / Java Config / 注解 (历史包袱重) |
| **性能** | **极高** (运行时绑定，启动快，内存占用小) | 较高 (启动时扫描类路径，较慢) |
| **侵入性** | **极低** (仅 `@Inject`，不强制继承类) | 较低，但生态庞大易形成依赖 |
| **功能范围** | **专注 DI/IoC** (轻量级，约 2MB) | 全家桶 (DI, AOP, Data, Web, Security 等) |
| **动态注入** | **支持** (运行时根据参数决定注入哪个实现) | 较弱 (主要靠 Profile 和 Qualifier) |
| **学习曲线** | **平缓** (核心概念少，几小时上手) | 陡峭 (生态太庞大) |

**我的观点**：
*   如果你在做**微服务、基础架构、中间件**（如 Elasticsearch、Presto 都在用 Guice），或者追求极致的性能和代码洁癖，**选 Guice**。
*   如果你在做**传统企业级 Web 应用**，需要快速集成数据库、安全、批处理等大量现成方案，**选 Spring**。

## 第七章：最佳实践与避坑指南

1.  **首选构造函数注入**：它能保证依赖不可变（`final` 字段），且便于单元测试（可以直接 `new` 对象传入 mock）。避免使用字段注入，因为它会破坏封装性，且难以测试。
2.  **使用 PrivateModule 封装细节**：如果你的模块依赖了其他模块的具体实现，使用 `PrivateModule` 将这些依赖隐藏起来，只暴露接口，实现真正的模块化。
3.  **不要滥用静态注入**：`requestStaticInjection()` 可以注入静态字段，但这是一种反模式，会增加耦合度，仅在集成遗留代码时谨慎使用。
4.  **利用 Provider 实现延迟加载**：对于重量级对象（如数据库连接池、大缓存），注入 `Provider<T>` 而不是 `T`，在真正需要时才调用 `get()` 方法创建。

## 结语

Google Guice 不仅仅是一个工具，它代表了一种**“代码即配置”**的现代 Java 开发哲学。它剔除了繁琐的 XML，利用编译器的类型检查能力帮你在写代码时就发现错误，而不是在深夜的生产环境报警中惊醒。

现在，打开你的 IDE，引入 Guice 依赖，体验一次 `@Inject` 带来的丝滑感受吧。记住：**好的架构，从告别 `new` 开始。**
