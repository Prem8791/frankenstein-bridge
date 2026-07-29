package com.android.internal.os.frankenstein;

import com.android.internal.os.frankenstein.BridgeInfo;
import com.android.internal.os.frankenstein.CapabilityEdge;
import com.android.internal.os.frankenstein.EventDescriptor;
import com.android.internal.os.frankenstein.EventSubscription;
import com.android.internal.os.frankenstein.ExternalProviderRegistration;
import com.android.internal.os.frankenstein.IBridgeEventCallback;
import com.android.internal.os.frankenstein.IBridgeOperation;
import com.android.internal.os.frankenstein.IBridgeSubscription;
import com.android.internal.os.frankenstein.ICatalogObserver;
import com.android.internal.os.frankenstein.IExternalCapabilityProvider;
import com.android.internal.os.frankenstein.OperationDescriptor;
import com.android.internal.os.frankenstein.OperationRequest;
import com.android.internal.os.frankenstein.PageRequest;
import com.android.internal.os.frankenstein.ProviderDescriptor;
import com.android.internal.os.frankenstein.ProviderPage;
import com.android.internal.os.frankenstein.SchemaDescriptor;

interface IFrankensteinBridge {
    BridgeInfo getBridgeInfo();
    ProviderPage listProviders(in PageRequest request);
    ProviderDescriptor describeProvider(String providerId, in int[] acceptedVersions);
    OperationDescriptor[] listOperations(String providerId, in PageRequest request);
    OperationDescriptor describeOperation(String providerId, String operationId,
            in int[] acceptedVersions);
    EventDescriptor[] listEvents(String providerId, in PageRequest request);
    EventDescriptor describeEvent(String providerId, String eventId, in int[] acceptedVersions);
    SchemaDescriptor getSchema(String schemaId, in int[] acceptedVersions);
    CapabilityEdge[] getCapabilityGraph(in PageRequest request);
    void observeCatalog(ICatalogObserver callback);
    IBridgeOperation startOperation(in OperationRequest request);
    IBridgeSubscription subscribeEvents(in EventSubscription subscription,
            IBridgeEventCallback callback);
    ExternalProviderRegistration registerExternalProvider(in ProviderDescriptor descriptor,
            IExternalCapabilityProvider provider);
    void updateExternalProvider(in ExternalProviderRegistration registration,
            in ProviderDescriptor descriptor);
    void unregisterExternalProvider(in ExternalProviderRegistration registration);
    IExternalCapabilityProvider getExternalProvider(String providerId,
            in int[] acceptedVersions);
}
