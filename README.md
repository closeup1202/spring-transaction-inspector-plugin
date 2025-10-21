# Spring Transaction Inspector

IntelliJ IDEA plugin for inspecting and analyzing Spring `@Transactional` annotations.

## Features

### 🔍 Smart Inspection
- **AOP Proxy Bypass Detection**: Warns when calling `@Transactional` methods within the same class
- **Invalid Modifiers Detection**: Detects `@Transactional` on private/final/static methods
- **N+1 Query Detection**: Identifies potential N+1 issues in loops and streams

### 🎯 Visual Indicators
- Gutter icons for `@Transactional` methods
- Different icons for read-only transactions
- Hover tooltips with transaction details

### 💡 Quick Fixes
- Automatic fixes for common issues
- Change method visibility
- Remove invalid modifiers
- Suppress inspection warnings

### ⚙️ Customization
- Settings UI to enable/disable features
- Configure which inspections to run
- Customize visual indicators

## Installation

1. Open IntelliJ IDEA
2. Go to `Settings > Plugins > Marketplace`
3. Search for "Spring Transaction Inspector"
4. Click `Install`
5. Restart IDE

## Usage

### Automatic Detection
The plugin automatically inspects your code and shows:
- Gutter icon for normal transactions
- Different gutter icon for read-only transactions
- Warning highlights for potential issues

### Manual Check
Right-click on a method > `Show Transaction Info`

### Settings
`Settings > Tools > Spring Transaction Inspector`

## Examples

### Same-class method call (AOP proxy bypass)
```java
@Service
public class UserService {
    
    @Transactional
    public void createUser() {
        updateUser();  // ⚠️ Warning: AOP proxy bypassed
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateUser() {
        // This won't run in a new transaction!
    }
}
```

### N+1 Query Detection
```java
@Transactional
public List<UserDTO> getUsers() {
    List<User> users = userRepository.findAll();
    
    return users.stream()
        .map(user -> new UserDTO(
            user.getName(),
            user.getPosts().size()  // ⚠️ Warning: N+1 query
        ))
        .collect(Collectors.toList());
}
```

## Requirements

- IntelliJ IDEA 2024.2+ (Community or Ultimate)
- Java 21+
- Spring Framework project

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

MIT License

## Author

[@closeup1202](https://github.com/closeup1202)

## Issues

Found a bug? [Report it here](https://github.com/closeup1202/spring-transaction-inspector-plugin/issues)