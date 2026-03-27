# Taplink SDK - keep public API
-keep class com.sunmi.tapro.taplink.sdk.** { *; }

# Service module public API
-keep interface com.sunmi.tapro.taplink.demo.service.PaymentService { *; }
-keep interface com.sunmi.tapro.taplink.demo.service.ConnectionListener { *; }
-keep interface com.sunmi.tapro.taplink.demo.service.PaymentCallback { *; }
-keep class com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService { *; }
-keep class com.sunmi.tapro.taplink.demo.service.PaymentResult { *; }
