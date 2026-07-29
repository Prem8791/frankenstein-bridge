#pragma once

#include <aidl/com/android/internal/os/frankenstein/diag/IFrankensteinDiagnostic.h>

#include <memory>

namespace aidl::com::android::internal::os::frankenstein::diag {

std::shared_ptr<IFrankensteinDiagnostic> CreateDiagnosticService();

}  // namespace aidl::com::android::internal::os::frankenstein::diag
