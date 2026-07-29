///////////////////////////////////////////////////////////////////////////////
// THIS FILE IS IMMUTABLE. DO NOT EDIT IN ANY CASE.                          //
///////////////////////////////////////////////////////////////////////////////

// This file is a snapshot of an AIDL file. Do not edit it manually. There are
// two cases:
// 1). this is a frozen version file - do not edit this in any case.
// 2). this is a 'current' file. If you make a backwards compatible change to
//     the interface (from the latest frozen version), the build system will
//     prompt you to update this file with `m <name>-update-api`.
//
// You must not make a backward incompatible change to any AIDL file built
// with the aidl_interface module type with versions property set. The module
// type is used to build AIDL files in a way that they can be used across
// independently updatable components of the system. If a device is shipped
// with such a backward incompatible change, it has a high risk of breaking
// later when a module using the interface is updated, e.g., Mainline modules.

package com.android.internal.os.frankenstein;
interface IFrankensteinBridge {
  com.android.internal.os.frankenstein.BridgeInfo getBridgeInfo();
  com.android.internal.os.frankenstein.ProviderPage listProviders(in com.android.internal.os.frankenstein.PageRequest request);
  com.android.internal.os.frankenstein.ProviderDescriptor describeProvider(String providerId, in int[] acceptedVersions);
  com.android.internal.os.frankenstein.OperationDescriptor[] listOperations(String providerId, in com.android.internal.os.frankenstein.PageRequest request);
  com.android.internal.os.frankenstein.OperationDescriptor describeOperation(String providerId, String operationId, in int[] acceptedVersions);
  com.android.internal.os.frankenstein.EventDescriptor[] listEvents(String providerId, in com.android.internal.os.frankenstein.PageRequest request);
  com.android.internal.os.frankenstein.EventDescriptor describeEvent(String providerId, String eventId, in int[] acceptedVersions);
  com.android.internal.os.frankenstein.SchemaDescriptor getSchema(String schemaId, in int[] acceptedVersions);
  com.android.internal.os.frankenstein.CapabilityEdge[] getCapabilityGraph(in com.android.internal.os.frankenstein.PageRequest request);
  void observeCatalog(com.android.internal.os.frankenstein.ICatalogObserver callback);
  com.android.internal.os.frankenstein.IBridgeOperation startOperation(in com.android.internal.os.frankenstein.OperationRequest request);
  com.android.internal.os.frankenstein.IBridgeSubscription subscribeEvents(in com.android.internal.os.frankenstein.EventSubscription subscription, com.android.internal.os.frankenstein.IBridgeEventCallback callback);
  com.android.internal.os.frankenstein.ExternalProviderRegistration registerExternalProvider(in com.android.internal.os.frankenstein.ProviderDescriptor descriptor, com.android.internal.os.frankenstein.IExternalCapabilityProvider provider);
  void updateExternalProvider(in com.android.internal.os.frankenstein.ExternalProviderRegistration registration, in com.android.internal.os.frankenstein.ProviderDescriptor descriptor);
  void unregisterExternalProvider(in com.android.internal.os.frankenstein.ExternalProviderRegistration registration);
  com.android.internal.os.frankenstein.IExternalCapabilityProvider getExternalProvider(String providerId, in int[] acceptedVersions);
}
