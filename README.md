# Spring Transaction Inspector

[![JetBrains Plugin](https://img.shields.io/badge/JetBrains-Plugin-blue.svg)](https://plugins.jetbrains.com/plugin/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

IntelliJ IDEA plugin for detecting common Spring `@Transactional` pitfalls and anti-patterns through static code analysis.

## 🎯 Why This Plugin?

Spring's `@Transactional` is powerful but has many gotchas that even experienced developers miss:
- Methods that silently ignore transaction settings due to AOP proxy bypass
- N+1 query performance issues that only show up in production
- Checked exceptions that don't trigger rollback, causing data inconsistency
- Transaction propagation conflicts that cause runtime errors

This plugin catches these issues **while you code**, before they reach production.

## ✨ Features

### 🔍 Comprehensive Inspections

#### 1. **AOP Proxy Bypass Detection**
Detects when `@Transactional` methods are called within the same class, causing transaction settings to be ignored.

```java
@Service
public class UserService {
    @Transactional
    public void createUser() {
        updateUser();  // ⚠️ AOP proxy bypassed - transaction settings ignored!
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUser() {
        // Won't run in a new transaction!
    }
}
```

#### 2. **Invalid Method Modifiers**
Prevents `@Transactional` on private/final/static methods where Spring AOP cannot intercept.

```java
@Service
public class OrderService {
    @Transactional
    private void processOrder() {  // ❌ Spring AOP cannot intercept private methods!
        orderRepository.save(order);
    }
}
```

#### 3. **N+1 Query Detection**
Identifies lazy-loaded relationships accessed in loops/streams that cause performance issues.

```java
@Transactional
public List<UserDTO> getUsers() {
    List<User> users = userRepository.findAll();  // 1 query

    return users.stream()
        .map(user -> new UserDTO(
            user.getName(),
            user.getPosts().size()  // ⚠️ N queries! (1 per user)
        ))
        .collect(Collectors.toList());
}
```

**Solution:**
```java
@Query("SELECT u FROM User u LEFT JOIN FETCH u.posts")
List<User> findAllWithPosts();  // ✅ Single query with JOIN
```

#### 4. **ReadOnly Transaction Write Operations**
Detects write operations (save/update/delete) in `@Transactional(readOnly=true)` methods.

```java
@Transactional(readOnly = true)
public void processData() {
    User user = userRepository.findById(1L);
    user.setName("Updated");
    userRepository.save(user);  // ⚠️ Write operation in readOnly transaction!
}
```

#### 5. **Checked Exception Rollback**
Warns when methods throw checked exceptions without `rollbackFor` configuration.

```java
@Transactional
public void processFile() throws IOException {
    orderRepository.save(order);        // DB write
    fileService.uploadFile(file);       // IOException thrown
    // ❌ Transaction commits despite exception! Data inconsistency!
}
```

**Solution:**
```java
@Transactional(rollbackFor = Exception.class)  // ✅ Rollback on all exceptions
public void processFile() throws IOException {
    // ...
}
```

#### 6. **@Async and @Transactional Conflicts** ⚡ NEW
Detects three critical async-transaction patterns:

**Pattern 1: @Async + @Transactional on same method**
```java
@Async
@Transactional  // ❌ Transaction doesn't propagate to async thread!
public void processAsync(User user) {
    userRepository.save(user);  // No transaction context!
}
```

**Pattern 2: Lazy loading in @Async methods**
```java
@Async
public void processUserPosts(User user) {
    int count = user.getPosts().size();  // ❌ LazyInitializationException!
}
```

**Pattern 3: Same-class @Async calls**
```java
@Service
public class UserService {
    public void createUser() {
        processAsync();  // ❌ Executes synchronously (AOP bypass)!
    }

    @Async
    private void processAsync() { }
}
```

#### 7. **ReadOnly Transaction Calling Write Methods** ⚡ NEW
Detects when `@Transactional(readOnly=true)` methods call write-capable methods, causing runtime errors.

```java
@Service
public class UserService {
    @Transactional(readOnly = true)
    public void viewUserData() {
        User user = userRepository.findById(1L);
        updateUserStats();  // 🔴 ERROR: Two problems!
    }

    @Transactional  // REQUIRED is default
    public void updateUserStats() {
        statsRepository.save(new Stats());
        // ❌ Runtime error: "Write operations not allowed in read-only mode"
    }
}
```

**Smart Detection:**
- **Same-class call**: Shows ERROR (not just warning) because `REQUIRES_NEW` won't work due to AOP bypass
- **Different-class call**: Shows WARNING with Quick Fix to change propagation to `REQUIRES_NEW`

**Solutions:**

✅ **Option 1: Extract to separate service**
```java
@Service
public class UserService {
    @Autowired
    private UserStatsService statsService;

    @Transactional(readOnly = true)
    public void viewUserData() {
        statsService.updateUserStats();  // ✅ OK
    }
}

@Service
public class UserStatsService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUserStats() { }
}
```

✅ **Option 2: Remove readOnly if writes needed**
```java
@Transactional  // readOnly=false by default
public void viewUserData() {
    User user = userRepository.findById(1L);
    updateUserStats();  // ✅ Both in write-capable transaction
}
```

### 🎨 Visual Indicators
- **Gutter icons** for `@Transactional` methods
- **Different icons** for read-only vs write transactions
- **Hover tooltips** with transaction configuration details

### ⚡ Quick Fixes
- Add `rollbackFor = Exception.class`
- Add specific exception types to `rollbackFor`
- Change method visibility (private → public/protected)
- Remove invalid modifiers (final/static)
- Change propagation to `REQUIRES_NEW`
- Remove conflicting annotations

### ⚙️ Customization
Fine-grained control over which inspections to enable:

**Settings → Tools → Spring Transaction Inspector**
- ✓ Detect same-class @Transactional method calls
- ✓ Warn on private methods with @Transactional
- ✓ Warn on final methods with @Transactional
- ✓ Warn on static methods with @Transactional
- ✓ Warn on checked exceptions without rollbackFor
- ✓ Detect @Async and @Transactional conflicts
- ✓ Detect write method calls from readOnly transactions
- ✓ Enable N+1 query detection
  - ✓ Check in stream operations (.map, .flatMap)
  - ✓ Check in for-each loops
- ✓ Show gutter icons for @Transactional methods
  - ✓ Show different icon for readOnly transactions

## 📦 Installation

### From JetBrains Marketplace (Recommended)
1. Open IntelliJ IDEA
2. Go to `Settings/Preferences → Plugins → Marketplace`
3. Search for **"Spring Transaction Inspector"**
4. Click `Install`
5. Restart IDE

### Manual Installation
1. Download the latest release from [Releases](https://github.com/closeup1202/spring-transaction-inspector-plugin/releases)
2. Go to `Settings/Preferences → Plugins → ⚙️ → Install Plugin from Disk`
3. Select the downloaded `.zip` file
4. Restart IDE

## 🚀 Usage

### Automatic Detection
Once installed, the plugin automatically analyzes your code:
- **Real-time warnings** appear as you type
- **Gutter icons** show transaction methods
- **Quick fixes** available via `Alt + Enter` (or `⌥ + ⏎` on Mac)

### Manual Inspection
Right-click on a method → `Show Transaction Info`

### Configuration
`Settings/Preferences → Tools → Spring Transaction Inspector`

## 💡 Best Practices Enforced

### ✅ DO
```java
// Separate concerns - different services
@Service
public class OrderService {
    @Autowired
    private NotificationService notificationService;

    @Transactional
    public void createOrder(Order order) {
        orderRepository.save(order);
        notificationService.sendEmail(order);  // ✅ Different class
    }
}

// Use @EntityGraph or JOIN FETCH for N+1
@Query("SELECT u FROM User u LEFT JOIN FETCH u.posts")
List<User> findAllWithPosts();

// Specify rollbackFor for checked exceptions
@Transactional(rollbackFor = Exception.class)
public void processFile() throws IOException { }
```

### ❌ DON'T
```java
// Same-class transactional call
@Service
public class OrderService {
    @Transactional
    public void createOrder() {
        notifyCustomer();  // ❌ Same class - AOP bypassed
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void notifyCustomer() { }
}

// Lazy loading in loops
List<User> users = userRepository.findAll();
for (User user : users) {
    user.getPosts().size();  // ❌ N+1 queries
}

// @Transactional on private method
@Transactional
private void saveOrder() { }  // ❌ AOP cannot intercept
```

## 📊 Supported Frameworks

- **Spring Framework** 5.x, 6.x
- **Spring Boot** 2.x, 3.x
- **JPA/Hibernate** (for N+1 detection)
- **Jakarta Persistence** (JPA 3.0+)

## 🔧 Requirements

- **IntelliJ IDEA** 2024.2+ (Community or Ultimate Edition)
- **Java** 21+
- **Kotlin** 2.1.0+ (for plugin development)

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

1. **Report bugs**: [Open an issue](https://github.com/closeup1202/spring-transaction-inspector-plugin/issues)
2. **Suggest features**: [Start a discussion](https://github.com/closeup1202/spring-transaction-inspector-plugin/discussions)
3. **Submit PRs**: Fork, code, test, and submit!

### Development Setup
```bash
git clone https://github.com/closeup1202/spring-transaction-inspector-plugin.git
cd spring-transaction-inspector-plugin
./gradlew runIde  # Launch IDE with plugin
./gradlew test    # Run tests
```

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👤 Author

**[@closeup1202](https://github.com/closeup1202)**

## 🙏 Acknowledgments

- Inspired by real-world Spring transaction issues encountered in production
- Built with [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)

## 📮 Support

- **Issues**: [GitHub Issues](https://github.com/closeup1202/spring-transaction-inspector-plugin/issues)
- **Discussions**: [GitHub Discussions](https://github.com/closeup1202/spring-transaction-inspector-plugin/discussions)

---

**If this plugin helped you catch a bug, please ⭐ star the repo!**
