package com.frankenbridge.broker.api;

import com.frankenbridge.broker.api.BrokerActionRequest;
import com.frankenbridge.broker.api.BrokerActionResult;

/** Replaceable app-facing API. This is not the permanent ROM ABI. */
interface IBridgeBroker {
    String probeBridge();
    BrokerActionResult executeAction(in BrokerActionRequest request);
}
