#include "service.h"

#include <android/binder_manager.h>
#include <android/binder_process.h>

int main() {
    ABinderProcess_setThreadPoolMaxThreadCount(4);
    auto service =
            aidl::com::android::internal::os::frankenstein::diag::CreateDiagnosticService();
    constexpr const char* kInstance =
            "com.android.internal.os.frankenstein.diag.IFrankensteinDiagnostic/default";
    if (AServiceManager_addService(service->asBinder().get(), kInstance) != STATUS_OK) return 1;
    ABinderProcess_joinThreadPool();
    return 0;
}
