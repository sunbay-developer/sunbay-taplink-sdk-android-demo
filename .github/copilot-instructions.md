# Taplink SDK Android Demo - Copilot Instructions

This document provides essential context for AI agents working on the taplink-sdk-android-demo project.

## Project Overview

**taplink-sdk-android-demo** is an Android demonstration project showcasing SUNBAY Taplink payment SDK integration for POS applications. It includes:

- **app-compose**: Modern Jetpack Compose + Material 3 demo UI (primary)
- **app**: Legacy XML/Activity-based demo UI  
- **lib_service**: Shared payment abstraction layer and Taplink SDK wrapper

**Tech Stack**: Kotlin 2.2.21 | AGP 8.13.1 | Compose | Material 3 | StateFlow | Coroutines | Min SDK 25

## Build & Run Commands

### Build
```bash
./gradlew build                    # Build all modules
./gradlew :app-compose:build       # Just Compose app
./gradlew :app:build               # Just legacy app
./gradlew :lib_service:build       # Just service library
```

### Install & Run
```bash
./gradlew :app-compose:installDebug      # Install & run Compose app (main)
./gradlew :app:installDebug              # Install & run legacy app
./gradlew :app-compose:assembleRelease   # Create release APK
```

### Build Variants
- **Dimensions**: `env` (dev, check/UAT, prod) × `workMode` (certification, standalone)
- **Configuration**: Use `local.secrets.properties` (and `local.properties` for SDK path)

### Testing
- **No automated test suite** — testing is manual via Tapro app/terminal connection
- Validate connection modes: App-to-App, Cable (USB), LAN, Cloud
- Test transaction flows: SALE, AUTH, REFUND, VOID, QUERY, etc.

## Architecture & Module Boundaries

### Dependency Flow
```
┌──────────────────────────────────────┐
│ UI Layer                             │
│ • app-compose: MVI + Compose         │
│ • app: Activity-based + XML          │
└────────────────┬─────────────────────┘
                 ↓
┌──────────────────────────────────────┐
│ lib_service (Shared)                 │
│ • PaymentService (interface)         │
│ • TaplinkPaymentService (SDK impl)   │
│ • CloudPaymentService (HTTP impl)    │
│ • ConnectionManager (state/lifecycle)│
└────────────────┬─────────────────────┘
                 ↓
┌──────────────────────────────────────┐
│ Taplink SDK AAR (1.0.7.19-release)   │
└────────────────┬─────────────────────┘
                 ↓
         ┌───────────────┐
         │ Tapro Device  │
         │ (Terminal)    │
         └───────────────┘
```

### lib_service Module
- **PaymentService**: Interface defining contract (connect, sale, auth, refund, void, query, abort, etc.)
- **TaplinkPaymentService**: Wraps Taplink SDK AAR; handles intent-based and cable connections
- **CloudPaymentService**: HTTP client for cloud/API mode
- **ConnectionManager**: Unified connection state + generation counter to prevent stale callbacks

### app-compose Module (Primary)
- **MVI Architecture**: Model (immutable state) → View (Compose UI) → Intent (user actions) → ViewModel
- **DependencyProvider**: Manual DI with lazy singletons (app-level singleton pattern)
- **TransactionRepository**: Persists transactions to SharedPreferences (Gson); observes via StateFlow
- **ConnectionManager**: Reactive StateFlow for connection state; generation counter prevents race conditions
- **Payment Flow**: ProductGrid → OrderSummary → PaymentDialog/ProgressScreen → TransactionHistory

## Project Conventions & Patterns

### 1. MVI (Model-View-Intent) - app-compose
Every feature follows the MVI pattern:
- **State** (`*State.kt`): Data class with immutable properties (e.g., `MainState(products, totalAmount, connectionStatus, message)`)
- **Intent** (`*Intent.kt`): Sealed class representing user actions (e.g., `AddProduct`, `ProcessPayment`, `UpdateConnection`)
- **ViewModel** (`*ViewModel.kt`): Updates state via `_state.update { copy(...) }` in response to intents
- **Compose UI** (`*Screen.kt`): Observes state via `state.collectAsStateWithLifecycle()`, dispatches intents

**Example**:
```kotlin
// State: immutable
data class MainState(
  val products: List<Product> = emptyList(),
  val orderItems: List<OrderItem> = emptyList(),
  val totalAmount: Long = 0,
  val connectionStatus: ConnectionStatus = DISCONNECTED,
  val message: Message? = null
)

// Intent: user action
sealed class MainIntent {
  data class AddProduct(val product: Product) : MainIntent()
  data class RemoveOrderItem(val order: OrderItem) : MainIntent()
  data object ProcessPayment : MainIntent()
}

// ViewModel: reducer
fun reduce(state: MainState, intent: MainIntent): MainState =
  when (intent) {
    is AddProduct -> state.copy(orderItems = state.orderItems + OrderItem(intent.product))
    is ProcessPayment -> state.copy(inProgress = true)
  }
```

### 2. Dependency Injection (Manual, No Framework)
DI is manual via [DependencyProvider.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/di/DependencyProvider.kt):
- Lazy singleton initialization at app-level
- No DI framework (Koin is optional transitive dependency)
- Simplifies debugging and avoids reflection overhead

**Pattern**:
```kotlin
object DependencyProvider {
  val paymentService by lazy { TaplinkPaymentService.getInstance() }
  val connectionManager by lazy { ConnectionManager.getInstance() }
  val transactionRepository by lazy { TransactionRepository.getInstance() }
}

// In Application.onCreate():
val service = DependencyProvider.paymentService
```

### 3. Repository Pattern (Persistence + State)
[TransactionRepository.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/repository/TransactionRepository.kt):
- Persists data to SharedPreferences with Gson serialization
- Exposes StateFlow for reactive updates
- Handles CRUD + query operations

**Pattern**:
```kotlin
class TransactionRepository {
  private val _transactions = MutableStateFlow<List<Transaction>>(emptyList())
  val transactions: StateFlow<List<Transaction>> = _transactions.asStateFlow()

  fun addTransaction(tx: Transaction) {
    _transactions.update { it + tx }
    // save to SharedPreferences
  }
}
```

### 4. Connection Management (Generation Counter Pattern)
[ConnectionManager.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/service/ConnectionManager.kt):
- **Generation counter**: Increments on connection switch; prevents stale async callbacks
- **StateFlow**: Exposes reactive connection state (current device, mode, type)
- **Unified interface**: Single API for App-to-App, Cable (USB/Serial), LAN, Cloud

**Pattern - Stale Callback Prevention**:
```kotlin
private val generation = AtomicInteger(0)

fun connect(mode: ConnectionMode) {
  generation.incrementAndGet()
  val currentGen = generation.get()
  
  // Later, in async callback:
  if (generation.get() == currentGen) {
    // This is the active connection — process callback
  } else {
    // Stale callback — ignore
  }
}
```

### 5. Error Handling (Centralized Error Codes)
Error codes are centralized in [Constants.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/util/Constants.kt) with ranges:
- **2xx**: SDK initialization / configuration errors
- **3xx**: Connection errors
- **4xx+**: Payment / transaction errors

**Pattern**:
```kotlin
object ErrorCodes {
  const val SDK_NOT_INITIALIZED = "201"
  const val CONNECTION_FAILED = "214"
  const val TRANSACTION_FAILED = "410"
}

// ErrorHandler maps codes to user-friendly messages
val message = ErrorHandler.getErrorMessage(code)
```

Error messages are wrapped in a `Message` model:
```kotlin
data class Message(
  val type: MessageType,        // INFO, SUCCESS, ERROR, WARNING
  val title: String,
  val content: String,
  val actions: List<Action> = emptyList()
)
```

### 6. Logging Utility (Enhanced Context)
[TLog.kt](lib_service/src/main/java/com/sunmi/tapro/taplink/demo/service/util/TLog.kt):
- Auto-captures caller file, line number, method name via stack trace inspection
- Simplifies debugging without manual context injection

**Pattern**:
```kotlin
TLog.d("Payment started")
// Output: [ (TLog.kt:45)#log ] [ (PaymentService.kt:120)#processSale ] Payment started
```

### 7. Data Class Immutability
All state objects, models, and DTOs are immutable data classes (Kotlin):
- Reduces bugs from unintended mutations
- Enables efficient state snapshots in MVI
- Works cleanly with `.copy()` for state updates

## Key Files & Examples

| Purpose | File | Details |
|---------|------|---------|
| **Service Contract** | [lib_service/PaymentService.kt](lib_service/src/main/java/com/sunmi/tapro/taplink/demo/service/PaymentService.kt) | Interface: connect, sale, auth, refund, void, query, abort |
| **Taplink SDK Wrapper** | [lib_service/TaplinkPaymentService.kt](lib_service/src/main/java/com/sunmi/tapro/taplink/demo/service/TaplinkPaymentService.kt) | Implements PaymentService; handles SDK lifecycle |
| **UI State** | [app-compose/ui/screens/main/MainState.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/ui/screens/main/MainState.kt) | MVI state model |
| **User Intents** | [app-compose/ui/screens/main/MainIntent.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/ui/screens/main/MainIntent.kt) | MVI intent sealed class |
| **ViewModel Logic** | [app-compose/ui/screens/main/MainViewModel.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/ui/screens/main/MainViewModel.kt) | State updates and side effects |
| **DI Container** | [app-compose/di/DependencyProvider.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/di/DependencyProvider.kt) | Lazy singleton initialization |
| **Transaction Persistence** | [app-compose/repository/TransactionRepository.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/repository/TransactionRepository.kt) | SharedPreferences + StateFlow |
| **Connection Lifecycle** | [app-compose/service/ConnectionManager.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/service/ConnectionManager.kt) | Generation counter + reactive state |
| **Error Codes** | [app-compose/util/Constants.kt](app-compose/src/main/java/com/sunmi/tapro/taplink/demo/util/Constants.kt) | Centralized error code ranges |
| **Logging** | [lib_service/util/TLog.kt](lib_service/src/main/java/com/sunmi/tapro/taplink/demo/service/util/TLog.kt) | Enhanced logging with caller context |

## Development Workflow

### Adding a New Feature
1. Add state to `*State.kt` (e.g., new UI field or loading flag)
2. Add intents to `*Intent.kt` (e.g., user action that modifies state)
3. Implement reducer in `*ViewModel.kt` (logic triggered by intent)
4. Update Compose UI in `*Screen.kt` (render state, dispatch intents)
5. If feature requires persistence, update **TransactionRepository**
6. If feature requires async operations, use `viewModelScope.launch { ... }` with proper error handling

### Connecting to Taplink SDK
- Use `PaymentService` interface in your feature (inject via `DependencyProvider`)
- Implement actual connectivity in `TaplinkPaymentService` or `CloudPaymentService`
- Leverage `ConnectionManager` for unified connection state
- Use generation counter pattern to prevent stale callbacks

### Adding Tests
- No existing automated test suite; testing is manual via Tapro app
- Consider adding unit tests for ViewModels (`@Test fun testAddProduct() { ... }`)
- Consider adding integration tests for PaymentService interactions

### Configuration & Secrets
- **local.properties**: SDK path (`sdk.dir=/path/to/Android/sdk`)
- **local.secrets.properties**: Merchant credentials, API keys (copy from `local.secrets.properties.example`)
- Build variants: dev, check (UAT), prod × certification, standalone

## Common Patterns & Anti-Patterns

### ✅ DO
- Use immutable data classes for all models and state
- Update state via `_state.update { copy(...) }` (not direct assignment)
- Handle coroutine cancellation via `viewModelScope`
- Use StateFlow and `collectAsStateWithLifecycle()` for Compose state observation
- Leverage generation counter pattern for connection state race conditions
- Centralize error codes and log error context (file, line, method)

### ❌ DON'T
- Mutate state objects directly (use `.copy()`)
- Pass mutable lists/maps in state without careful synchronization
- Ignore coroutine scope — always use `viewModelScope` in ViewModels
- Add DI framework dependencies without strong justification (manual injection is lightweight)
- Hard-code error messages — use Error codes and ErrorHandler
- Print to console without using TLog for production builds

## Key Package Structure

### app-compose
```
com.sunmi.tapro.taplink.demo
├── di/                           # DependencyProvider
├── model/                        # Transaction, Product, Message, OrderItem, ConnectionMode
├── repository/                   # TransactionRepository
├── service/                      # ConnectionManager, PaymentCallbackExtensions
├── ui/
│   ├── screens/                  # POS app screens (main, progress, list, detail, settings)
│   │   └── main/                 # MainState, MainIntent, MainViewModel, MainScreen (MVI)
│   ├── components/               # Reusable Compose components
│   ├── navigation/               # Navigation routes (NavGraph)
│   └── theme/                    # Material 3 theming
├── util/                         # Constants, ErrorHandler, Preferences, RetryManager, AmountFormatter
└── TaplinkDemoApplication.kt      # Application class; DI initialization
```

### lib_service
```
com.sunmi.tapro.taplink.demo.service
├── PaymentService.kt             # Interface contract
├── TaplinkPaymentService.kt       # Taplink SDK wrapper
├── CloudPaymentService.kt         # Cloud/HTTP implementation
├── ConnectionManager.kt           # Unified connection + generation counter
├── cloud/                        # Cloud HTTP client and response mapping
└── util/                         # TLog, AmountConverter, Constants
```

## Resources & Links

- [README.md](../README.md) — Project overview, quick start, prerequisites, troubleshooting
- [build-variants.gradle](../build-variants.gradle) — Build flavor definitions
- [gradle/libs.versions.toml](../gradle/libs.versions.toml) — Centralized dependency versions
- [Taplink SDK Documentation](https://developer.sunbay.io/taplink) — Payment flow, connection modes, APIs
- [Jetpack Compose Guide](https://developer.android.com/compose)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

## Questions & Support

For issues, refer to:
1. README.md troubleshooting section
2. Local `build-variants.gradle` and dependency versions
3. PaymentService interface for available APIs
4. ConnectionManager for connection state management questions
