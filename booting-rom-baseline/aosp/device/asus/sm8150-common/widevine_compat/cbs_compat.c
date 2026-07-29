/*
 * Compatibility export for legacy Widevine blobs built against a BoringSSL
 * release where CBS_init was a dynamic libcrypto symbol.
 */

#include <stddef.h>
#include <stdint.h>

typedef struct cbs_st {
    const uint8_t* data;
    size_t len;
} CBS;

__attribute__((visibility("default")))
void CBS_init(CBS* cbs, const uint8_t* data, size_t len) {
    if (cbs == NULL) {
        return;
    }
    cbs->data = data;
    cbs->len = len;
}
