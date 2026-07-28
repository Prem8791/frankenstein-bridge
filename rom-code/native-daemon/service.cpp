#include <aidl/com/android/internal/os/frankenstein/diag/BnFrankensteinDiagnostic.h>
#include <aidl/com/android/internal/os/frankenstein/diag/DiagRequest.h>
#include <aidl/com/android/internal/os/frankenstein/diag/DiagResult.h>
#include <android-base/file.h>
#include <android-base/properties.h>
#include <android-base/unique_fd.h>
#include <android/binder_manager.h>
#include <android/binder_process.h>
#include <android/binder_ibinder.h>
#include <cutils/properties.h>
#include <fcntl.h>
#include <linux/openat2.h>
#include <selinux/selinux.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <array>
#include <atomic>
#include <cerrno>
#include <cstdint>
#include <map>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

namespace aidl::com::android::internal::os::frankenstein::diag {
namespace {

constexpr int32_t kOk = 0;
constexpr int32_t kInvalidArgument = 1;
constexpr int32_t kPermissionDenied = 2;
constexpr int32_t kUnavailable = 5;
constexpr int32_t kResourceExhausted = 7;
constexpr int64_t kMaxStreamBytes = 64LL * 1024 * 1024;

int64_t Generation() {
    timespec now {};
    clock_gettime(CLOCK_BOOTTIME, &now);
    return static_cast<int64_t>(now.tv_sec) * 1'000'000'000LL + now.tv_nsec;
}

struct Root {
    const char* name;
    const char* path;
};

constexpr std::array<Root, 8> kRoots = {{
        {"tombstones", "/data/tombstones"},
        {"pstore", "/sys/fs/pstore"},
        {"anr", "/data/anr"},
        {"recovery", "/cache/recovery"},
        {"update", "/data/misc/update_engine"},
        {"kernel", "/proc"},
        {"audit", "/sys/fs/selinux"},
        {"build", "/system/etc/frankenstein"},
}};

const Root* FindRoot(const std::string& name) {
    for (const auto& root : kRoots) {
        if (name == root.name) return &root;
    }
    return nullptr;
}

::android::base::unique_fd OpenConfined(const Root& root, const std::string& opaque,
                                     int flags) {
    if (opaque.empty() || opaque.front() == '/' || opaque.find("..") != std::string::npos) {
        errno = EINVAL;
        return {};
    }
    ::android::base::unique_fd directory(open(root.path, O_PATH | O_DIRECTORY | O_CLOEXEC));
    if (directory.get() < 0) return {};
    open_how how = {
            .flags = static_cast<uint64_t>(flags | O_CLOEXEC | O_NOFOLLOW),
            .mode = 0,
            .resolve = RESOLVE_BENEATH | RESOLVE_NO_MAGICLINKS | RESOLVE_NO_SYMLINKS,
    };
    return ::android::base::unique_fd(static_cast<int>(
            syscall(SYS_openat2, directory.get(), opaque.c_str(), &how, sizeof(how))));
}

void CborText(std::vector<uint8_t>* out, const std::string& value) {
    const size_t length = value.size();
    if (length < 24) {
        out->push_back(static_cast<uint8_t>(0x60 | length));
    } else {
        out->insert(out->end(), {0x78, static_cast<uint8_t>(length)});
    }
    out->insert(out->end(), value.begin(), value.end());
}

std::vector<uint8_t> StringMap(
        const std::vector<std::pair<std::string, std::string>>& entries) {
    std::vector<uint8_t> out;
    if (entries.size() < 24) out.push_back(static_cast<uint8_t>(0xa0 | entries.size()));
    for (const auto& [key, value] : entries) {
        CborText(&out, key);
        CborText(&out, value);
    }
    return out;
}

bool IsRedactedProperty(const std::string& name) {
    return name.starts_with("ro.serial") || name.starts_with("ro.boot.vbmeta.digest") ||
            name.find("key") != std::string::npos || name.find("token") != std::string::npos ||
            name.find("credential") != std::string::npos;
}

bool IsDeniedService(const std::string& name) {
    static constexpr std::array<const char*, 10> kDenied = {
            "keystore", "keymint", "gatekeeper", "widevine", "drm",
            "secure_element", "weaver", "authsecret", "identity", "credstore",
    };
    for (const char* token : kDenied) {
        if (name.find(token) != std::string::npos) return true;
    }
    return false;
}

const std::map<std::string, std::pair<std::string, std::string>>& WritableProperties() {
    static const std::map<std::string, std::pair<std::string, std::string>> kAllowlist = {};
    return kAllowlist;
}

class DiagnosticService final : public BnFrankensteinDiagnostic {
  public:
    DiagnosticService() : generation_(Generation()) {}

    ndk::ScopedAStatus getGeneration(int64_t* generation) override {
        *generation = generation_;
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus execute(const DiagRequest& request, DiagResult* result) override {
        result->generation = generation_;
        result->declaredLength = -1;
        result->truncated = false;
        switch (request.operation) {
            case 1:
                return ServiceOperation(request, result);
            case 2:
                return ArtifactOperation(request, result);
            case 3:
                return SelinuxOperation(request, result);
            case 4:
                return PropertyOperation(request, result);
            case 5:
                return BootOperation(request, result);
            default:
                result->code = kInvalidArgument;
                return ndk::ScopedAStatus::ok();
        }
    }

    ndk::ScopedAStatus cancel(int64_t operation_id) override {
        std::lock_guard guard(cancel_lock_);
        cancelled_[operation_id] = true;
        return ndk::ScopedAStatus::ok();
    }

  private:
    ndk::ScopedAStatus ServiceOperation(const DiagRequest& request, DiagResult* result) {
        if (request.opaqueId == "list") {
            result->schemaId = "diag.services.v1";
            result->payload = StringMap({{"availability", "runtime-query"},
                                         {"raw_transactions", "disabled"}});
            result->code = kOk;
            return ndk::ScopedAStatus::ok();
        }
        if (request.opaqueId.starts_with("describe:")) {
            std::string service = request.opaqueId.substr(9);
            bool declared = AServiceManager_isDeclared(service.c_str());
            result->schemaId = "diag.services.v1";
            result->payload = StringMap({{"service", service},
                                         {"declared", declared ? "true" : "false"}});
            result->code = kOk;
            return ndk::ScopedAStatus::ok();
        }
        if (request.opaqueId.starts_with("dump:")) {
            std::string service_name = request.opaqueId.substr(5);
            if (service_name.empty() || IsDeniedService(service_name)) {
                result->code = kPermissionDenied;
                return ndk::ScopedAStatus::ok();
            }
            ndk::SpAIBinder target(AServiceManager_checkService(service_name.c_str()));
            if (target.get() == nullptr) {
                result->code = kUnavailable;
                return ndk::ScopedAStatus::ok();
            }
            int pipe_fds[2];
            if (pipe2(pipe_fds, O_CLOEXEC | O_NONBLOCK) != 0) {
                result->code = kResourceExhausted;
                return ndk::ScopedAStatus::ok();
            }
            ::android::base::unique_fd read_end(pipe_fds[0]);
            ::android::base::unique_fd write_end(pipe_fds[1]);
            result->stream = ndk::ScopedFileDescriptor(read_end.release());
            result->declaredLength = -1;
            result->truncated = true;
            result->schemaId = "diag.services.dump.v1";
            result->code = kOk;
            std::thread([target = std::move(target), sink = std::move(write_end)]() mutable {
                const char* args[] = {"--proto"};
                AIBinder_dump(target.get(), sink.get(), args, 1);
            }).detach();
            return ndk::ScopedAStatus::ok();
        }
        result->code = kInvalidArgument;
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus ArtifactOperation(const DiagRequest& request, DiagResult* result) {
        const Root* root = FindRoot(request.scope);
        if (root == nullptr) {
            result->code = kPermissionDenied;
            return ndk::ScopedAStatus::ok();
        }
        if (request.opaqueId == "list") {
            result->schemaId = "diag.artifacts.v1";
            result->payload = StringMap({{"root", root->name}, {"access", "metadata-only"}});
            result->code = kOk;
            return ndk::ScopedAStatus::ok();
        }
        ::android::base::unique_fd source = OpenConfined(*root, request.opaqueId, O_RDONLY);
        if (source.get() < 0) {
            result->code = errno == EACCES ? kPermissionDenied : kUnavailable;
            return ndk::ScopedAStatus::ok();
        }
        struct stat metadata {};
        if (fstat(source.get(), &metadata) != 0 || !S_ISREG(metadata.st_mode)) {
            result->code = kInvalidArgument;
            return ndk::ScopedAStatus::ok();
        }
        int pipe_fds[2];
        if (pipe2(pipe_fds, O_CLOEXEC | O_NONBLOCK) != 0) {
            result->code = kResourceExhausted;
            return ndk::ScopedAStatus::ok();
        }
        ::android::base::unique_fd read_end(pipe_fds[0]);
        ::android::base::unique_fd write_end(pipe_fds[1]);
        const int64_t offset = std::max<int64_t>(0, request.offset);
        const int64_t limit = std::min<int64_t>(
                kMaxStreamBytes, request.length <= 0 ? kMaxStreamBytes : request.length);
        result->stream = ndk::ScopedFileDescriptor(read_end.release());
        result->declaredLength = std::min<int64_t>(
                std::max<int64_t>(0, metadata.st_size - offset), limit);
        result->truncated = metadata.st_size - offset > limit;
        result->schemaId = "diag.artifacts.v1";
        result->code = kOk;
        std::thread([source = std::move(source), sink = std::move(write_end), offset, limit]() mutable {
            std::array<uint8_t, 32 * 1024> buffer {};
            int64_t transferred = 0;
            while (transferred < limit) {
                size_t want = std::min<int64_t>(buffer.size(), limit - transferred);
                ssize_t count = pread(source.get(), buffer.data(), want, offset + transferred);
                if (count <= 0) break;
                ssize_t written = write(sink.get(), buffer.data(), count);
                if (written <= 0) break;
                transferred += written;
            }
        }).detach();
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus SelinuxOperation(const DiagRequest& request, DiagResult* result) {
        result->schemaId = "diag.selinux.v1";
        if (request.opaqueId == "state") {
            result->payload = StringMap({{"enforcing", security_getenforce() == 1 ? "true" : "false"},
                                         {"policy_version",
                                          std::to_string(security_policyvers())}});
            result->code = kOk;
        } else if (request.opaqueId == "self_context") {
            char* context = nullptr;
            if (getcon(&context) == 0 && context != nullptr) {
                result->payload = StringMap({{"context", context}});
                freecon(context);
                result->code = kOk;
            } else {
                result->code = kUnavailable;
            }
        } else if (request.opaqueId.starts_with("check:")) {
            std::vector<std::string> fields;
            size_t start = 6;
            for (;;) {
                size_t separator = request.opaqueId.find(':', start);
                fields.push_back(request.opaqueId.substr(
                        start, separator == std::string::npos ? separator : separator - start));
                if (separator == std::string::npos) break;
                start = separator + 1;
            }
            if (fields.size() != 4) {
                result->code = kInvalidArgument;
            } else {
                int allowed = selinux_check_access(fields[0].c_str(), fields[1].c_str(),
                                                   fields[2].c_str(), fields[3].c_str(), nullptr);
                result->payload = StringMap({{"allowed", allowed == 0 ? "true" : "false"}});
                result->code = kOk;
            }
        } else {
            result->code = kInvalidArgument;
        }
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus PropertyOperation(const DiagRequest& request, DiagResult* result) {
        result->schemaId = "diag.properties.v1";
        if (request.opaqueId.starts_with("read:")) {
            std::string name = request.opaqueId.substr(5);
            if (IsRedactedProperty(name)) {
                result->code = kPermissionDenied;
            } else {
                result->payload = StringMap(
                        {{"name", name}, {"value", ::android::base::GetProperty(name, "")}});
                result->code = kOk;
            }
        } else if (request.opaqueId.starts_with("write:")) {
            result->code = WritableProperties().contains(request.opaqueId.substr(6))
                    ? kOk : kPermissionDenied;
        } else {
            result->code = kInvalidArgument;
        }
        return ndk::ScopedAStatus::ok();
    }

    ndk::ScopedAStatus BootOperation(const DiagRequest&, DiagResult* result) {
        result->schemaId = "diag.boot.v1";
        result->payload = StringMap({
                {"slot_suffix", ::android::base::GetProperty("ro.boot.slot_suffix", "")},
                {"verified_boot_state",
                 ::android::base::GetProperty("ro.boot.verifiedbootstate", "unknown")},
                {"boot_reason", ::android::base::GetProperty("ro.boot.bootreason", "unknown")},
                {"boot_control", "read-only"},
        });
        result->code = kOk;
        return ndk::ScopedAStatus::ok();
    }

    const int64_t generation_;
    std::mutex cancel_lock_;
    std::map<int64_t, bool> cancelled_;
};

}  // namespace
}  // namespace aidl::com::android::internal::os::frankenstein::diag

namespace aidl::com::android::internal::os::frankenstein::diag {

std::shared_ptr<IFrankensteinDiagnostic> CreateDiagnosticService() {
    return ndk::SharedRefBase::make<DiagnosticService>();
}

}  // namespace aidl::com::android::internal::os::frankenstein::diag
