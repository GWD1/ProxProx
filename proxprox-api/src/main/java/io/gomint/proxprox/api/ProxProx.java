package io.gomint.proxprox.api;

import com.google.common.base.Preconditions;
import lombok.Getter;

public final class ProxProx {

    @Getter
    private static Proxy proxy;

    public static void setProxy(Proxy proxy) {
        Preconditions.checkState(ProxProx.proxy == null, "Proxy server instance already assigned - Cannot reassign");
        ProxProx.proxy = proxy;
    }

}
