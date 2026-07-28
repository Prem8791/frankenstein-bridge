#include "service.h"

#include <fuzzbinder/libbinder_ndk_driver.h>
#include <fuzzer/FuzzedDataProvider.h>

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    auto service =
            aidl::com::android::internal::os::frankenstein::diag::CreateDiagnosticService();
    android::fuzzService(service->asBinder().get(), FuzzedDataProvider(data, size));
    return 0;
}
