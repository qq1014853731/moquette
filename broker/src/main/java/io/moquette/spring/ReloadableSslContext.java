package io.moquette.spring;

import io.netty.handler.ssl.DelegatingSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.ObjectUtil;

import javax.net.ssl.SSLEngine;

/**
 * @author 楚孔响
 * @version 1.0
 * @date 2023/2/27 17:27
 */
public class ReloadableSslContext extends DelegatingSslContext {

	private volatile SslContext ctx;

	public ReloadableSslContext(SslContext ctx) {
        super(ctx);
        this.ctx = ObjectUtil.checkNotNull(ctx, "ctx");
	}

    @Override
    protected void initEngine(SSLEngine sslEngine) {
    }

    /**
	 * 替换为新的sslContext
	 * @param newContext 新的sslContext
	 */
	public synchronized void reload(SslContext newContext) {
		synchronized (ctx) {
			ctx = newContext;
		}
	}
}
